package com.example.messenger.data.ws

import com.example.messenger.data.ApiConfig
import com.example.messenger.data.dto.Message
import com.example.messenger.data.local.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Отказ до accept: 4001 — невалидный JWT, 4003/4004 — нет доступа к чату,
 * 4009/4029 — кап одновременных соединений и частота подключений.
 * На этих кодах не переподключаемся: счётчики пополняются каждым handshake,
 * поэтому reconnect только продлевает бан.
 */
private val NO_RECONNECT_CODES = setOf(4001, 4003, 4004, 4009, 4029)

/** Чат удалён или мы выбыли из него — его надо убрать из списка */
val CHAT_GONE_CODES = setOf(4003, 4004)

fun closeNotice(code: Int): String? = when (code) {
    4001 -> "Сессия истекла, войдите заново"
    4003 -> "Вы больше не участник этого чата"
    4004 -> "Этот чат удалён"
    4009 -> "Слишком много открытых соединений, закройте другие чаты"
    4029 -> "Слишком частые переподключения, подождите немного"
    else -> null
}

private const val RECONNECT_DELAY_MS = 2_000L
private const val CLOSE_TRANSPORT = -1

private fun wsRequest(url: String): Request = Request.Builder()
    .url(url)
    .header("Origin", ApiConfig.WS_ORIGIN)
    .build()

/**
 * Каркас соединения: один сокет за раз, переподключение с фиксированной задержкой.
 * Все колбэки проверяют, что событие относится к текущему сокету, — иначе события
 * соединения, закрытого при смене чата, попали бы в новый.
 */
private class WsPipe(
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
    private val request: suspend () -> Request?,
    private val onFrame: (String) -> Unit,
    private val onOpen: () -> Unit,
    private val onClosed: (code: Int, willReconnect: Boolean) -> Unit,
) {
    @Volatile
    private var ws: WebSocket? = null

    fun open() {
        close()
        connect()
    }

    fun close() {
        val socket = ws ?: return
        ws = null
        socket.close(1000, null)
    }

    fun send(text: String): Boolean = ws?.send(text) ?: false

    private fun connect() {
        scope.launch(Dispatchers.IO) {
            val target = request() ?: return@launch
            ws = client.newWebSocket(
                target,
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        if (webSocket !== ws) return
                        onOpen()
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        if (webSocket !== ws) return
                        onFrame(text)
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        if (webSocket !== ws) return
                        ws = null
                        webSocket.close(code, null)
                        val retry = code !in NO_RECONNECT_CODES
                        onClosed(code, retry)
                        if (retry) scheduleReconnect()
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        if (webSocket !== ws) return
                        ws = null
                        // Обрыв транспорта: кода нет, но переподключаться можно
                        onClosed(response?.code ?: CLOSE_TRANSPORT, true)
                        scheduleReconnect()
                    }
                },
            )
        }
    }

    private fun scheduleReconnect() {
        scope.launch {
            delay(RECONNECT_DELAY_MS)
            if (ws == null) connect()
        }
    }
}

/**
 * Сокет одного чата: `ws/chat/<id>/?token=<jwt>`.
 * Presence и last_seen ведёт личный канал, здесь только события открытого чата.
 */
class ChatSocket internal constructor(
    client: OkHttpClient,
    store: TokenStore,
    private val json: Json,
    scope: CoroutineScope,
) {
    var onMessage: ((Message) -> Unit)? = null
    var onUserStatus: ((userId: String, status: String) -> Unit)? = null
    var onMessagesRead: ((readerId: String) -> Unit)? = null
    var onInitialPresence: ((List<String>) -> Unit)? = null

    /** Сервер отклонил событие (пустой текст, длина, анти-флуд) — соединение живое */
    var onRejected: ((String) -> Unit)? = null
    var onConnected: (() -> Unit)? = null
    var onDisconnected: ((code: Int, willReconnect: Boolean) -> Unit)? = null

    private var chatId: String? = null

    private val pipe = WsPipe(
        client = client,
        scope = scope,
        request = {
            val id = chatId
            val token = if (id != null) store.access() else null
            if (id != null && token != null) {
                wsRequest("${ApiConfig.BASE_WS}chat/$id/?token=$token")
            } else {
                null
            }
        },
        onFrame = ::handle,
        onOpen = { onConnected?.invoke() },
        onClosed = { code, retry -> onDisconnected?.invoke(code, retry) },
    )

    fun open(chatId: String) {
        this.chatId = chatId
        pipe.open()
    }

    fun send(text: String): Boolean = pipe.send(json.encodeToString(TextPayload(text)))

    fun close() {
        chatId = null
        pipe.close()
    }

    private fun handle(text: String) {
        val frame = runCatching { json.parseToJsonElement(text) as? JsonObject }
            .getOrNull() ?: return
        runCatching {
            frame["error"]?.jsonPrimitive?.contentOrNull?.let {
                onRejected?.invoke(it)
                return
            }
            when (frame["type"]?.jsonPrimitive?.contentOrNull) {
                "user_status" -> {
                    val uid = frame["user_id"]?.jsonPrimitive?.contentOrNull
                    val status = frame["status"]?.jsonPrimitive?.contentOrNull
                    if (uid != null && status != null) onUserStatus?.invoke(uid, status)
                }

                "messages_read" -> frame["reader_id"]?.jsonPrimitive?.contentOrNull
                    ?.let { onMessagesRead?.invoke(it) }

                "initial_presence" -> onInitialPresence?.invoke(
                    frame["user_ids"]?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                        ?: emptyList(),
                )

                // Новое сообщение приходит без поля type, формат идентичен REST
                null -> frame.let { raw ->
                    val message = runCatching {
                        json.decodeFromJsonElement(Message.serializer(), raw)
                    }.getOrNull()
                    if (message != null) onMessage?.invoke(message)
                }
            }
        }
    }

    @Serializable
    private data class TextPayload(val text: String)
}

/**
 * Личный канал `ws/notifications/`: события по всем чатам пользователя — сокет
 * конкретного чата живёт, только пока чат открыт.
 */
class NotificationSocket internal constructor(
    client: OkHttpClient,
    store: TokenStore,
    private val json: Json,
    scope: CoroutineScope,
) {
    var onNewMessage: ((chatId: String, message: Message, unreadCount: Int) -> Unit)? = null
    var onChatRead: ((chatId: String) -> Unit)? = null
    var onChatDeleted: ((chatId: String) -> Unit)? = null
    var onChatRenamed: ((chatId: String, name: String) -> Unit)? = null
    var onMemberRemoved: ((chatId: String) -> Unit)? = null
    var onConnected: (() -> Unit)? = null
    var onDisconnected: ((code: Int, willReconnect: Boolean) -> Unit)? = null

    private val pipe = WsPipe(
        client = client,
        scope = scope,
        request = {
            store.access()?.let { wsRequest("${ApiConfig.BASE_WS}notifications/?token=$it") }
        },
        onFrame = ::handle,
        onOpen = { onConnected?.invoke() },
        onClosed = { code, retry -> onDisconnected?.invoke(code, retry) },
    )

    fun open() = pipe.open()

    fun close() = pipe.close()

    private fun handle(text: String) {
        val frame = runCatching { json.parseToJsonElement(text) as? JsonObject }
            .getOrNull() ?: return
        runCatching {
            val chat = frame["chat"]?.jsonPrimitive?.contentOrNull ?: return
            when (frame["type"]?.jsonPrimitive?.contentOrNull) {
                "new_message" -> {
                    val raw = frame["message"] ?: return
                    val message = runCatching {
                        json.decodeFromJsonElement(Message.serializer(), raw)
                    }.getOrNull() ?: return
                    val unread = frame["unread_count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                        ?: 0
                    onNewMessage?.invoke(chat, message, unread)
                }

                "chat_read" -> onChatRead?.invoke(chat)
                "chat_deleted" -> onChatDeleted?.invoke(chat)
                "member_removed" -> onMemberRemoved?.invoke(chat)
                "chat_renamed" -> {
                    val name = frame["name"]?.jsonPrimitive?.contentOrNull
                    if (name != null) onChatRenamed?.invoke(chat, name)
                }
            }
        }
    }
}

@Singleton
class SocketFactory @Inject constructor(
    private val client: OkHttpClient,
    private val store: TokenStore,
    private val json: Json,
) {
    /** Сокет привязан к области видимости ViewModel: уход с экрана закрывает соединение */
    fun chat(scope: CoroutineScope): ChatSocket = ChatSocket(client, store, json, scope)

    fun notifications(scope: CoroutineScope): NotificationSocket =
        NotificationSocket(client, store, json, scope)
}
