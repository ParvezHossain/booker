package com.parvez.booker.data.network

import android.content.Context
import android.util.Log
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
        Log.d("BookSseManager", "SSE connection stopped/paused")
    }

    /**
     * Closes the connection completely and clears session state on logout.
     */
    fun logout() {
        stop()
        currentUsername = ""
        currentPassword = ""
        handledEventIds.clear()
        Log.d("BookSseManager", "SSE connection logged out")
    }

    private fun connectInternal() {
        if (isStopped || currentUsername.isBlank()) return

        eventSource?.cancel()
        eventSource = null

        val cursor = cursorStorage.getCursor(currentUsername)
        val url = "http://192.168.0.122:8080/api/books/events"

        Log.d("BookSseManager", "Connecting to SSE endpoint: $url (Saved Cursor: ${cursor ?: "None"})")

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
                Log.d("BookSseManager", "SSE Connection Established successfully (HTTP ${response.code})")
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (isStopped) return

                Log.d("BookSseManager", "SSE Event Received - ID: $id, Type: $type, Data: $data")

                val effectiveType = type ?: "message"

                when (effectiveType) {
                    "ready" -> {
                        val effectiveId = if (!id.isNullOrBlank()) id else extractIdFromData(data)
                        if (!effectiveId.isNullOrBlank()) {
                            Log.d("BookSseManager", "Ready event cursor saved: $effectiveId")
                            cursorStorage.saveCursor(currentUsername, effectiveId)
                        }
                    }
                    "book.created" -> {
                        try {
                            val eventPayload = gson.fromJson(data, BookEvent::class.java)
                            val eventId = if (!id.isNullOrBlank()) id else eventPayload?.eventId ?: ""

                            if (eventId.isNotEmpty() && handledEventIds.contains(eventId)) {
                                Log.d("BookSseManager", "Duplicate event ignored: $eventId")
                                return
                            }

                            if (eventId.isNotEmpty()) {
                                handledEventIds.add(eventId)
                                cursorStorage.saveCursor(currentUsername, eventId)
                                Log.d("BookSseManager", "Cursor updated to event ID: $eventId")
                            }

                            val newBook = eventPayload?.book
                            if (newBook != null) {
                                Log.d("BookSseManager", "Emitting new book event for: ${newBook.title}")
                                scope.launch {
                                    _eventsFlow.emit(BookSseEvent.BookCreated(newBook, eventId))
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("BookSseManager", "Error parsing book.created SSE event data", e)
                        }
                    }
                }
            }

            override fun onClosed(eventSource: EventSource) {
                isConnectedOrConnecting = false
                Log.d("BookSseManager", "SSE Stream closed by server")
                if (!isStopped) {
                    scheduleReconnect()
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                isConnectedOrConnecting = false
                Log.e("BookSseManager", "SSE Failure - HTTP ${response?.code}: ${t?.message}", t)

                if (isStopped) return

                val responseCode = response?.code

                when (responseCode) {
                    401 -> {
                        Log.e("BookSseManager", "401 Auth Error received on SSE stream")
                        scope.launch {
                            _eventsFlow.emit(BookSseEvent.AuthError)
                        }
                    }
                    400 -> {
                        Log.e("BookSseManager", "400 Invalid Cursor error received. Resynchronizing...")
                        cursorStorage.clearCursor(currentUsername)
                        scope.launch {
                            _eventsFlow.emit(BookSseEvent.ResyncRequired)
                        }
                        scheduleReconnect()
                    }
                    else -> {
                        scheduleReconnect()
                    }
                }
            }
        })
    }

    private fun extractIdFromData(data: String): String? {
        return try {
            val map = gson.fromJson(data, Map::class.java)
            map["id"]?.toString() ?: map["eventId"]?.toString()
        } catch (e: Exception) {
            null
        }
    }

    private fun scheduleReconnect() {
        if (isStopped) return

        reconnectAttempt++
        val baseDelayMs = min(30_000L, 3000L * (2.0.pow(reconnectAttempt - 1)).toLong())
        val jitterMs = Random.nextLong(0, 1000L)
        val totalDelayMs = baseDelayMs + jitterMs

        Log.d("BookSseManager", "Scheduling SSE reconnect attempt #$reconnectAttempt in ${totalDelayMs}ms")

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