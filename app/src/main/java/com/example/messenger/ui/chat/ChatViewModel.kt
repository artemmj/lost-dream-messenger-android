package com.example.messenger.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.messenger.data.api.ChatApi
import com.example.messenger.data.api.UserApi
import com.example.messenger.data.displayMessage
import com.example.messenger.data.dto.ChatDetail
import com.example.messenger.data.dto.Message
import com.example.messenger.data.dto.MemberBody
import com.example.messenger.data.dto.RenameChatBody
import com.example.messenger.data.dto.SendMessageBody
import com.example.messenger.data.dto.User
import com.example.messenger.data.resultOf
import com.example.messenger.data.ws.CHAT_GONE_CODES
import com.example.messenger.data.ws.SocketFactory
import com.example.messenger.data.ws.closeNotice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SocketPhase { Connecting, Connected, Reconnecting, Closed }

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatApi: ChatApi,
    private val userApi: UserApi,
    socketFactory: SocketFactory,
) : ViewModel() {

    var chatId by mutableStateOf<String?>(null)
        private set
    var myId by mutableStateOf<String?>(null)
        private set
    var messages by mutableStateOf<List<Message>>(emptyList())
        private set
    var details by mutableStateOf<ChatDetail?>(null)
        private set

    /** Кто из участников сейчас в сети — снимок `initial_presence` + переходы `user_status` */
    var online by mutableStateOf<Set<String>>(emptySet())
        private set

    var input by mutableStateOf("")
    var phase by mutableStateOf(SocketPhase.Connecting)
        private set

    /** Плашка под шапкой: отказ сервера, лимиты, коды закрытия сокета */
    var notice by mutableStateOf<String?>(null)

    var loadError by mutableStateOf<String?>(null)
        private set
    var loadingHistory by mutableStateOf(false)
        private set
    var hasOlder by mutableStateOf(false)
        private set

    /** Чат исчез (удалён или выбыли) — экрану пора закрываться */
    var chatGone by mutableStateOf(false)
        private set

    private var openedId: String? = null
    private var page = 1
    private var hadConnection = false

    private val socket = socketFactory.chat(viewModelScope)

    init {
        socket.onMessage = { message ->
            addMessage(message)
            // Мы смотрим в этот чат — всё пришедшее прочитано
            if (message.sender.id != myId) markRead()
        }
        socket.onInitialPresence = { ids -> online = ids.toSet() }
        socket.onUserStatus = { userId, status ->
            online = if (status == "online") online + userId else online - userId
        }
        socket.onMessagesRead = { readerId ->
            // Флаг is_read глобальный на сообщение, поэтому прочтение кем-то гасит его у всех
            if (readerId != myId) messages = messages.map { it.copy(is_read = true) }
        }
        socket.onRejected = { notice = it }
        socket.onConnected = {
            phase = SocketPhase.Connected
            if (hadConnection) reloadFirstPage()
            hadConnection = true
        }
        socket.onDisconnected = { code, willReconnect ->
            phase = if (willReconnect) SocketPhase.Reconnecting else SocketPhase.Closed
            notice = closeNotice(code) ?: "Соединение потеряно"
            if (code in CHAT_GONE_CODES) chatGone = true
        }
    }

    /** Вызывается при каждом входе на экран, включая возврат по back-stack */
    fun open(id: String, me: String) {
        chatId = id
        myId = me
        if (openedId != id) {
            openedId = id
            online = emptySet()
            notice = null
            loadError = null
        }
        loadDetails()
        reloadFirstPage()
        markRead()
        socket.open(id)
    }

    fun send() {
        val text = input.trim()
        val id = chatId ?: return
        if (text.isEmpty()) return
        input = ""
        if (socket.send(text)) {
            notice = null
            return
        }
        // Сокет не открыт — уходим в REST; текст вернём в поле, если и он откажет
        viewModelScope.launch {
            val result = resultOf { chatApi.send(id, SendMessageBody(text)) }
            result
                .onSuccess { addMessage(it) }
                .onFailure {
                    input = text
                    notice = it.displayMessage()
                }
        }
    }

    /** Следующая страница — сообщения ещё старше; сервер внутри страницы уже сортирует по времени */
    fun loadOlder() {
        val id = chatId ?: return
        if (!hasOlder || loadingHistory) return
        loadingHistory = true
        viewModelScope.launch {
            val result = resultOf { chatApi.messages(id, page + 1) }
            result
                .onSuccess { next ->
                    page += 1
                    val known = messages.map { it.id }.toSet()
                    messages = next.results.filterNot { it.id in known } + messages
                    hasOlder = next.next != null
                }
                .onFailure { loadError = it.displayMessage() }
            loadingHistory = false
        }
    }

    fun markRead() {
        val id = chatId ?: return
        viewModelScope.launch {
            // Успех молча: бейдж снимет событие chat_read в личном канале
            resultOf { chatApi.markRead(id) }
        }
    }

    fun loadDetails() {
        val id = chatId ?: return
        viewModelScope.launch {
            val result = resultOf { chatApi.chat(id) }
            result
                .onSuccess { details = it }
                .onFailure { loadError = it.displayMessage() }
        }
    }

    /** @param onDone получает текст ошибки или null при успехе — чтобы знать, закрывать диалог */
    fun rename(name: String, onDone: (String?) -> Unit) {
        val id = chatId ?: return
        viewModelScope.launch {
            val result = resultOf { chatApi.rename(id, RenameChatBody(name)) }
            result.onSuccess { details = it }
            onDone(result.exceptionOrNull()?.displayMessage())
        }
    }

    fun deleteChat() {
        val id = chatId ?: return
        viewModelScope.launch {
            val result = resultOf { chatApi.deleteChat(id) }
            result
                // Остальным участникам рассылает сервер; нам надо уйти самим
                .onSuccess { chatGone = true }
                .onFailure { notice = it.displayMessage() }
        }
    }

    fun addMember(userId: String) {
        val id = chatId ?: return
        viewModelScope.launch {
            resultOf { chatApi.addMember(id, MemberBody(userId)) }
                .onFailure { notice = it.displayMessage() }
            loadDetails()
        }
    }

    /** Выход из чата: user_id == myId. Единственного админа сервер удалить не даст */
    fun removeMember(userId: String) {
        val id = chatId ?: return
        viewModelScope.launch {
            val result = resultOf { chatApi.removeMember(id, MemberBody(userId)) }
            result
                .onSuccess { if (userId == myId) chatGone = true else loadDetails() }
                .onFailure { notice = it.displayMessage() }
        }
    }

    var memberQuery by mutableStateOf("")
    var memberResults by mutableStateOf<List<User>>(emptyList())

    /** Поиск кандидатов в участники: debounce делает экран, здесь только запрос */
    fun searchCandidates() {
        val query = memberQuery.trim()
        if (query.length < MIN_SEARCH) {
            memberResults = emptyList()
            return
        }
        viewModelScope.launch {
            val result = resultOf { userApi.search(query).results }
            result.onSuccess { memberResults = it }.onFailure { memberResults = emptyList() }
        }
    }

    /** Плашку под шапкой убираем по тапу: перекрывать ленту она должна по времени, а не навсегда */
    fun dismissErrors() {
        loadError = null
        notice = null
    }

    fun close() {
        socket.close()
    }

    override fun onCleared() {
        socket.close()
    }

    private fun addMessage(message: Message) {
        // Одна и та же отправка приходит и из REST, и из WS-broadcast — дедупликация по id
        if (messages.any { it.id == message.id }) return
        messages = messages + message
    }

    private fun reloadFirstPage() {
        val id = chatId ?: return
        viewModelScope.launch {
            val result = resultOf { chatApi.messages(id, 1) }
            result
                // После обрыва сокета добваем то, что пришло без нас; известные id не дублируем
                .onSuccess { first ->
                    val freshIds = first.results.map { it.id }.toSet()
                    page = 1
                    hasOlder = first.next != null
                    messages = messages.filterNot { it.id in freshIds } + first.results
                }
                .onFailure { loadError = it.displayMessage() }
        }
    }
}

private const val MIN_SEARCH = 2
