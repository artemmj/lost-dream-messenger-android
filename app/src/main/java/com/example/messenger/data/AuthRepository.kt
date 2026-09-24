package com.example.messenger.data

import com.example.messenger.data.api.AuthApi
import com.example.messenger.data.api.UserApi
import com.example.messenger.data.dto.LoginRequest
import com.example.messenger.data.dto.Me
import com.example.messenger.data.dto.RegisterRequest
import com.example.messenger.data.local.TokenStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ошибки не оборачиваем в Result: всем вызовам нужен один и тот же текст из
 * DRF-ответа, он извлекается в ViewModel через displayMessage().
 */
@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val userApi: UserApi,
    private val store: TokenStore,
) {
    suspend fun login(phone: String, password: String): Me {
        val tokens = authApi.login(LoginRequest(phone, password))
        store.save(tokens.access, tokens.refresh)
        return userApi.me()
    }

    /** /auth/register/ отдаёт токены сразу, вторым запросом за ним идёт логин */
    suspend fun register(request: RegisterRequest): Me {
        val tokens = authApi.register(request)
        store.save(tokens.access, tokens.refresh)
        return userApi.me()
    }

    suspend fun logout() = store.clear()
}
