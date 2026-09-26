package com.parvez.booker.data.network

import android.content.Context
import com.google.gson.Gson
import com.parvez.booker.data.model.Book
import com.parvez.booker.data.model.BookEvent
import com.parvez.booker.data.repository.CursorStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Shared, lifecycle-managed Server-Sent Events (SSE) client for GET /api/books/events.
 */
class BookSseManager(
    context: Context,
    private val cursorStorage: CursorStorage = CursorStorage(context)
) {
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _eventsFlow = MutableSharedFlow<BookSseEvent>(extraBufferCapacity = 64)
    val eventsFlow: SharedFlow<BookSseEvent> = _eventsFlow.asSharedFlow()

    private var eventSource: EventSource? = null
    private var isConnectedOrConnecting = false
    private var isStopped = true
    private var reconnectAttempt = 0

    private var currentUsername: String = ""
    private var currentPassword: String = ""

    // In-memory set of handled event IDs to prevent duplicate processing during replay bursts
    private val handledEventIds = ConcurrentHashMap.newKeySet<String>()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // SSE stream long-polling read timeout disabled
        .connectTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.NONE // Avoid logging sensitive headers
        })
        .build()

    /**
     * Starts or resumes the shared SSE connection using active basic auth credentials.
     */
    fun start(usernameInput: String, passwordInput: String) {
        if (usernameInput.isBlank() || passwordInput.isBlank()) return

        currentUsername = usernameInput
        currentPassword = passwordInput
        isStopped = false
        reconnectAttempt = 0

        connectInternal()
    }

    /**
     * Pauses the connection (e.g. when application enters background).
     */
    fun stop() {
        isStopped = true
        isConnectedOrConnecting = false
        eventSource?.cancel()
        eventSource = null
    }

    /**
     * Closes the connection completely and clears session state on logout.
     */
    fun logout() {
        stop()
        currentUsername = ""
        currentPassword = ""
        handledEventIds.clear()
    }

    private fun connectInternal() {
        if (isStopped || currentUsername.isBlank()) return

        eventSource?.cancel()
        eventSource = null

        val cursor = cursorStorage.getCursor(currentUsername)
        val url = "http://192.168.0.122:8080/api/books/events"

        val requestBuilder = Request.Builder()
            .url(url)
            .header("Authorization", Credentials.basic(currentUsername, currentPassword))
            .header("Accept", "text/event-stream")

        if (!cursor.isNullOrBlank()) {
            requestBuilder.header("Last-Event-ID", cursor)
        }

        val request = requestBuilder.build()
        val factory = EventSources.createFactory(okHttpClient)

        isConnectedOrConnecting = true

        eventSource = factory.newEventSource(request, object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                isConnectedOrConnecting = true
                reconnectAttempt = 0
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (isStopped) return

                val effectiveType = type ?: "message"

                when (effectiveType) {
                    "ready" -> {
                        if (!id.isNullOrBlank()) {
                            cursorStorage.saveCursor(currentUsername, id)
                        }
                    }
                    "book.created" -> {
                        try {
                            val eventPayload = gson.fromJson(data, BookEvent::class.java)
                            val eventId = id ?: eventPayload?.eventId ?: ""

                            if (eventId.isNotEmpty() && handledEventIds.contains(eventId)) {
                                // Duplicate event, ignore idempotently
                                return
                            }

                            if (eventId.isNotEmpty()) {
                                handledEventIds.add(eventId)
                                cursorStorage.saveCursor(currentUsername, eventId)
                            }

                            val newBook = eventPayload?.book
                            if (newBook != null) {
                                scope.launch {
                                    _eventsFlow.emit(BookSseEvent.BookCreated(newBook, eventId))
                                }
                            }
                        } catch (e: Exception) {
                            // Invalid JSON payload
                        }
                    }
                }
            }

            override fun onClosed(eventSource: EventSource) {
                isConnectedOrConnecting = false
                if (!isStopped) {
                    scheduleReconnect()
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                isConnectedOrConnecting = false

                if (isStopped) return

                val responseCode = response?.code

                when (responseCode) {
                    401 -> {
                        scope.launch {
                            _eventsFlow.emit(BookSseEvent.AuthError)
                        }
                    }
                    400 -> {
                        // Invalid cursor: reset cursor and request full resynchronization
                        cursorStorage.clearCursor(currentUsername)
                        scope.launch {
                            _eventsFlow.emit(BookSseEvent.ResyncRequired)
                        }
                        scheduleReconnect()
                    }
                    else -> {
                        // 503, network failure, or normal closure: retry with backoff and jitter
                        scheduleReconnect()
                    }
                }
            }
        })
    }

    private fun scheduleReconnect() {
        if (isStopped) return

        reconnectAttempt++
        // Bounded exponential backoff: 3s base, max 30s + random jitter
        val baseDelayMs = min(30_000L, 3000L * (2.0.pow(reconnectAttempt - 1)).toLong())
        val jitterMs = Random.nextLong(0, 1000L)
        val totalDelayMs = baseDelayMs + jitterMs

        scope.launch {
            delay(totalDelayMs)
            if (!isStopped) {
                connectInternal()
            }
        }
    }
}

/**
 * Sealed class representing SSE event outputs for UI / ViewModel consumption.
 */
sealed class BookSseEvent {
    data class BookCreated(val book: Book, val eventId: String) : BookSseEvent()
    object ResyncRequired : BookSseEvent()
    object AuthError : BookSseEvent()
}