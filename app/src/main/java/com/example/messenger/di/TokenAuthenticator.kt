package com.example.messenger.di

import com.example.messenger.data.ApiConfig
import com.example.messenger.data.dto.RefreshRequest
import com.example.messenger.data.dto.RefreshResponse
import com.example.messenger.data.local.TokenStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Отдельный «чистый» клиент для refresh — иначе interceptor/authenticator
 * зацикливают сами себя.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val store: TokenStore,
    private val json: Json,
) : Authenticator {

    private val refreshClient = OkHttpClient()

    override fun authenticate(route: Route?, response: Response): Request? {
        val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")
            ?: return null

        // Параллельный запрос уже успел обновить токен — повторяем с новым
        val stored = store.accessBlocking()
        if (stored != null && failedToken != stored) {
            return retryWith(response, stored)
        }

        synchronized(this) {
            // Пока ждали блокировки, токен мог обновиться здесь же
            val current = store.accessBlocking()
            if (current != null && current != failedToken) return retryWith(response, current)

            val refreshed = runCatching { refreshBlocking() }.getOrNull()
            if (refreshed == null) {
                // Refresh не принял — сессия кончилась, возвращаем на экран входа
                runBlocking { store.clear() }
                return null
            }
            return retryWith(response, refreshed)
        }
    }

    private fun refreshBlocking(): String? {
        val refresh = store.refreshBlocking() ?: return null
        val body = json.encodeToString(RefreshRequest.serializer(), RefreshRequest(refresh))
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(ApiConfig.BASE_HTTP + "auth/refresh/")
            .post(body)
            .build()
        return refreshClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val text = resp.body?.string() ?: return null
            json.decodeFromString(RefreshResponse.serializer(), text).access
                .also { runBlocking { store.updateAccess(it) } }
        }
    }

    private fun retryWith(response: Response, token: String): Request =
        response.request.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
}
