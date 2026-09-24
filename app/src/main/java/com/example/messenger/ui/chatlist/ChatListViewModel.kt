package com.example.messenger.ui.chatlist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.messenger.data.api.ChatApi
import com.example.messenger.data.displayMessage
import com.example.messenger.data.dto.ChatList
import com.example.messenger.data.dto.Message
import com.example.messenger.data.resultOf
import com.example.messenger.data.ws.SocketFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Список чатов + личный WS-канал. Этот ViewModel живёт, пока экран в стеке навигации,
 * то есть канал не рвётся, когда открыт конкретный чат, — именно через него приходят
 * сообщения в остальные чаты и сброс бейджей с других устройств.
 */
@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val chatApi: ChatApi,
    socketFactory: SocketFactory,
) : ViewModel() {

    var chats by mutableStateOf<List<ChatList>>(emptyList())
        private set

    var loading by mutableStateOf(false)
        private set

    var realTime by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)

    private val socket = socketFactory.notifications(viewModelScope)

    init {
        socket.onNewMessage = { chatId, message, unread -> applyNewMessage(chatId, message, unread) }
        socket.onChatRead = { chatId -> setUnread(chatId, 0) }
        socket.onChatDeleted = { chatId -> forget(chatId) }
        socket.onMemberRemoved = { chatId -> forget(chatId) }
        socket.onChatRenamed = { chatId, name ->
            chats = chats.map { if (it.id == chatId) it.copy(name = name) else it }
        }
        socket.onConnected = {
            realTime = true
            // Пока канал был закрыт, могли прийти новые сообщения и чужие прочтения
            refresh(silent = true)
        }
        socket.onDisconnected = { _, _ -> realTime = false }
        socket.open()
        refresh()
    }

    fun refresh(silent: Boolean = false) {
        if (loading) return
        loading = true
        if (!silent) error = null
        viewModelScope.launch {
            val result = resultOf { chatApi.chats().results }
            loading = false
            result
                .onSuccess {
                    chats = it
                    error = null
                }
                .onFailure { if (!silent) error = it.displayMessage() }
        }
    }

    override fun onCleared() {
        socket.close()
    }

    private fun applyNewMessage(chatId: String, message: Message, unread: Int) {
        val known = chats.any { it.id == chatId }
        if (!known) {
            // Нас только что добавили в чат — одного элемента мало, перечитываем список
            refresh(silent = true)
            return
        }
        chats = chats.map {
            if (it.id == chatId) it.copy(last_message = message, unread_count = unread) else it
        }
    }

    private fun setUnread(chatId: String, count: Int) {
        chats = chats.map { if (it.id == chatId) it.copy(unread_count = count) else it }
    }

    private fun forget(chatId: String) {
        chats = chats.filterNot { it.id == chatId }
    }
}
