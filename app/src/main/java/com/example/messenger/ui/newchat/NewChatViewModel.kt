package com.example.messenger.ui.newchat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.messenger.data.api.ChatApi
import com.example.messenger.data.api.UserApi
import com.example.messenger.data.displayMessage
import com.example.messenger.data.dto.CHAT_TYPE_GROUP
import com.example.messenger.data.dto.CreateChatBody
import com.example.messenger.data.dto.PrivateChatBody
import com.example.messenger.data.resultOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NewChatViewModel @Inject constructor(
    private val chatApi: ChatApi,
    private val userApi: UserApi,
) : ViewModel() {

    var query by mutableStateOf("")
    var results by mutableStateOf<List<Hit>>(emptyList())
    var groupName by mutableStateOf("")

    /** id -> участник: порядок в списке важен для чипов, поэтому держим список */
    var selected by mutableStateOf<List<Hit>>(emptyList())
        private set

    var error by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    /** Поиск запускает экран с debounce; здесь только сам запрос и порог длины */
    fun search() {
        val text = query.trim()
        if (text.length < MIN_QUERY) {
            results = emptyList()
            return
        }
        viewModelScope.launch {
            val result = resultOf { userApi.search(text).results.map { Hit(it.id, it.displayName, it.phone) } }
            result
                .onSuccess { results = it }
                // Сервер отвечает и «ничего не найдено» пустой страницей — ошибку не затираем
                .onFailure { error = it.displayMessage() }
        }
    }

    fun toggle(hit: Hit) {
        selected = if (selected.any { it.id == hit.id }) {
            selected.filterNot { it.id == hit.id }
        } else {
            selected + hit
        }
    }

    fun clearSelected() {
        selected = emptyList()
    }

    /** POST /chats/private/ идемпотентен: повторный вызов вернёт существующий чат */
    fun openPrivate(hit: Hit, onCreated: (String) -> Unit) {
        if (busy) return
        busy = true
        error = null
        viewModelScope.launch {
            resultOf { chatApi.privateChat(PrivateChatBody(hit.id)).id }
                .onSuccess(onCreated)
                .onFailure { error = it.displayMessage() }
            busy = false
        }
    }

    fun createGroup(onCreated: (String) -> Unit) {
        if (busy) return
        val name = groupName.trim()
        if (name.isEmpty()) {
            error = "Укажите название группы"
            return
        }
        busy = true
        error = null
        viewModelScope.launch {
            val result = resultOf {
                chatApi.createChat(
                    CreateChatBody(
                        type = CHAT_TYPE_GROUP,
                        name = name,
                        member_ids = selected.map { it.id }.ifEmpty { null },
                    )
                ).id
            }
            result
                .onSuccess(onCreated)
                .onFailure { error = it.displayMessage() }
            busy = false
        }
    }
}

/** Строка результата: сериалайзеры /users/search/ и участников чата разные, экрану нужны только три поля */
data class Hit(val id: String, val title: String, val phone: String)

private const val MIN_QUERY = 2
