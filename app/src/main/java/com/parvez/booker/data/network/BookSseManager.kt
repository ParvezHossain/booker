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
import java.io.InterruptedIOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Shared, lifecycle-managed Server-Sent Events (SSE) client for GET /api/books/events.
 */
class BookSseManager(
    context: Context? = null,
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

    private var activeAccountEmail: String = ""
    private var activeWorkspaceId: String? = null
    private var currentUsername: String = ""
    private var currentPassword: String = ""

    // In-memory sets to prevent duplicate event processing and suppress own REST creation notifications
    private val handledEventIds = ConcurrentHashMap.newKeySet<String>()
    private val locallyCreatedBookKeys = ConcurrentHashMap.newKeySet<String>()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // SSE stream long-polling read timeout disabled
        .connectTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.NONE // Avoid logging sensitive headers
        })
        .build()

    /**
     * Starts or resumes the shared SSE connection using active account email and workspace ID.
     */
    fun startToken(email: String, workspaceId: String? = null) {
        if (email.isBlank()) return

        activeAccountEmail = email
        activeWorkspaceId = workspaceId
        isStopped = false
        reconnectAttempt = 0

        connectInternal()
    }

    /**
     * Registers a local REST POST creation to suppress duplicate system notifications when SSE event arrives.
     */
    fun markLocallyCreatedBook(bookId: Long?, title: String? = null) {
        if (bookId != null && bookId > 0) {
            locallyCreatedBookKeys.add("id_$bookId")
        }
        if (!title.isNullOrBlank()) {
            locallyCreatedBookKeys.add("title_${title.trim().lowercase()}")
        }
    }

    /**
     * Legacy start using Basic auth credentials.
     */
    fun start(usernameInput: String, passwordInput: String) {
        if (usernameInput.isBlank()) return

        activeAccountEmail = usernameInput
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
        activeAccountEmail = ""
        activeWorkspaceId = null
        currentUsername = ""
        currentPassword = ""
        handledEventIds.clear()
        locallyCreatedBookKeys.clear()
        Log.d("BookSseManager", "SSE connection logged out")
    }

    private fun connectInternal() {
        if (isStopped || activeAccountEmail.isBlank()) return

        eventSource?.cancel()
        eventSource = null

        val cursor = cursorStorage.getScopedCursor(RetrofitClient.baseUrl, activeAccountEmail, activeWorkspaceId)
            ?: cursorStorage.getCursor(activeAccountEmail)

        val url = "${RetrofitClient.baseUrl}books/events"

        val authHeader = when {
            RetrofitClient.accessToken.isNotEmpty() -> "Bearer ${RetrofitClient.accessToken}"
            currentUsername.isNotEmpty() -> Credentials.basic(currentUsername, currentPassword)
            else -> ""
        }

        if (authHeader.isBlank()) return

        Log.d("BookSseManager", "Connecting to SSE endpoint: $url (Saved Cursor: ${cursor ?: "None"})")

        val requestBuilder = Request.Builder()
            .url(url)
            .header("Authorization", authHeader)
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
                            cursorStorage.saveScopedCursor(RetrofitClient.baseUrl, activeAccountEmail, activeWorkspaceId, effectiveId)
                            cursorStorage.saveCursor(activeAccountEmail, effectiveId)
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
                                cursorStorage.saveScopedCursor(RetrofitClient.baseUrl, activeAccountEmail, activeWorkspaceId, eventId)
                                cursorStorage.saveCursor(activeAccountEmail, eventId)
                                Log.d("BookSseManager", "Cursor updated to event ID: $eventId")
                            }

                            val newBook = eventPayload?.book
                            if (newBook != null) {
                                val isOwnCreation = (newBook.id != null && locallyCreatedBookKeys.contains("id_${newBook.id}")) ||
                                        (!newBook.title.isNullOrBlank() && locallyCreatedBookKeys.contains("title_${newBook.title.trim().lowercase()}"))

                                Log.d("BookSseManager", "Emitting new book event for: ${newBook.title} (Own Creation: $isOwnCreation)")
                                scope.launch {
                                    _eventsFlow.emit(BookSseEvent.BookCreated(newBook, eventId, isOwnCreation = isOwnCreation))
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("BookSseManager", "Error parsing book.created SSE event data", e)
                        }
                    }
                    "public-book-request.reviewed" -> {
                        try {
                            val map = gson.fromJson(data, Map::class.java)
                            val eventId = if (!id.isNullOrBlank()) id else map["eventId"]?.toString() ?: ""
                            val requestId = map["requestId"]?.toString() ?: ""
                            val status = map["status"]?.toString() ?: "REVIEWED"
                            val bookId = (map["bookId"] as? Number)?.toLong()
                            val message = map["message"]?.toString()

                            if (eventId.isNotEmpty() && handledEventIds.contains(eventId)) return@onEvent
                            if (eventId.isNotEmpty()) {
                                handledEventIds.add(eventId)
                                cursorStorage.saveScopedCursor(RetrofitClient.baseUrl, activeAccountEmail, activeWorkspaceId, eventId)
                                cursorStorage.saveCursor(activeAccountEmail, eventId)
                            }

                            scope.launch {
                                _eventsFlow.emit(BookSseEvent.PublicRequestReviewed(requestId, status, bookId, message, eventId))
                            }
                        } catch (e: Exception) {
                            Log.e("BookSseManager", "Error parsing public-book-request.reviewed SSE event data", e)
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

                if (isStopped || t?.message == "canceled" || t is InterruptedIOException) {
                    Log.d("BookSseManager", "SSE Stream cancelled or stopped gracefully")
                    return
                }

                Log.e("BookSseManager", "SSE Failure - HTTP ${response?.code}: ${t?.message}", t)

                val responseCode = response?.code

                when (responseCode) {
                    401 -> {
                        Log.e("BookSseManager", "401 Auth Error received on SSE stream")
                        scope.launch {
                            val coordinator = RetrofitClient.sessionCoordinatorRef
                            val failedToken = RetrofitClient.accessToken
                            val newToken = try { coordinator?.performSingleFlightRefresh(failedToken) } catch (e: Exception) { null }

                            if (!newToken.isNullOrBlank()) {
                                Log.d("BookSseManager", "SSE refreshed token successfully; reconnecting stream")
                                connectInternal()
                            } else {
                                Log.e("BookSseManager", "Permanent auth failure on SSE stream; stopping reconnects")
                                isStopped = true
                                _eventsFlow.emit(BookSseEvent.AuthError)
                            }
                        }
                    }
                    400 -> {
                        Log.e("BookSseManager", "400 Invalid Cursor error received. Resynchronizing...")
                        cursorStorage.clearScopedCursor(RetrofitClient.baseUrl, activeAccountEmail, activeWorkspaceId)
                        cursorStorage.clearCursor(activeAccountEmail)
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
    data class BookCreated(
        val book: Book,
        val eventId: String,
        val isOwnCreation: Boolean = false
    ) : BookSseEvent()

    data class PublicRequestReviewed(
        val requestId: String,
        val status: String,
        val bookId: Long?,
        val message: String?,
        val eventId: String
    ) : BookSseEvent()

    object ResyncRequired : BookSseEvent()
    object AuthError : BookSseEvent()
}