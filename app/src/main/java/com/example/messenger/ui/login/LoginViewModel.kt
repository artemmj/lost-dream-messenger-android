package com.example.messenger.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.messenger.data.AuthRepository
import com.example.messenger.data.displayMessage
import com.example.messenger.data.dto.RegisterRequest
import com.example.messenger.data.resultOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repo: AuthRepository,
) : ViewModel() {

    var phone by mutableStateOf("")
    var password by mutableStateOf("")
    var isRegister by mutableStateOf(false)
    var email by mutableStateOf("")
    var firstName by mutableStateOf("")
    var lastName by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    var loading by mutableStateOf(false)
        private set

    fun toggleMode() {
        isRegister = !isRegister
        error = null
    }

    fun submit() {
        if (loading) return
        val trimmedPhone = phone.trim()
        if (trimmedPhone.isEmpty() || password.isEmpty()) {
            error = "Укажите телефон и пароль"
            return
        }
        loading = true
        error = null
        viewModelScope.launch {
            val result = resultOf {
                if (isRegister) {
                    repo.register(
                        RegisterRequest(
                            phone = trimmedPhone,
                            password = password,
                            password_confirm = password,
                            // Бэкенд трактует "" как невалидный email, поэтому пустые
                            // поля вырезаем
                            email = email.takeIf { it.isNotBlank() },
                            first_name = firstName.takeIf { it.isNotBlank() },
                            last_name = lastName.takeIf { it.isNotBlank() },
                        ),
                    )
                } else {
                    repo.login(trimmedPhone, password)
                }
            }
            loading = false
            // При успехе навигацию переключает Session: токен лёг в хранилище
            result.exceptionOrNull()?.let { error = it.displayMessage() }
        }
    }
}
