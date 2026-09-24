package com.example.messenger.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.messenger.data.api.UserApi
import com.example.messenger.data.displayMessage
import com.example.messenger.data.dto.Me
import com.example.messenger.data.dto.ProfileUpdate
import com.example.messenger.data.resultOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userApi: UserApi,
) : ViewModel() {

    var phone by mutableStateOf("")
    var email by mutableStateOf("")
    var firstName by mutableStateOf("")
    var lastName by mutableStateOf("")

    var lastSeen by mutableStateOf<String?>(null)
        private set

    /** Профиль подгружается отдельным запросом: в JWT только user_id */
    var loading by mutableStateOf(false)
        private set
    var loaded by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var notice by mutableStateOf<String?>(null)

    init {
        load()
    }

    fun load() {
        loading = true
        viewModelScope.launch {
            resultOf { userApi.me() }
                .onSuccess { fill(it) }
                .onFailure { error = it.displayMessage() }
            loading = false
        }
    }

    /** PATCH принимает любые поля; в ответ отдаём серверные значения — телефон и email он нормализует */
    fun save() {
        if (loading) return
        loading = true
        error = null
        notice = null
        viewModelScope.launch {
            val body = ProfileUpdate(
                phone = phone.trim(),
                email = email.trim(),
                first_name = firstName.trim(),
                last_name = lastName.trim(),
            )
            resultOf { userApi.updateMe(body) }
                // Ответ совпадает с GET /users/me/ — просто доводим поля до него
                .onSuccess { me ->
                    fill(me)
                    notice = "Сохранено"
                }
                .onFailure { error = it.displayMessage() }
            loading = false
        }
    }

    private fun fill(me: Me) {
        phone = me.phone
        email = me.email
        firstName = me.first_name
        lastName = me.last_name
        lastSeen = me.last_seen
        loaded = true
    }
}
