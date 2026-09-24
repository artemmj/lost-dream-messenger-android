package com.example.messenger.data

import com.example.messenger.data.api.UserApi
import com.example.messenger.data.dto.Me
import com.example.messenger.data.local.TokenStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SessionState {
    /** Пока не прочитан токена — показывать нечего, но и на вход рановато */
    data object Unknown : SessionState

    data object LoggedOut : SessionState

    data class LoggedIn(val me: Me) : SessionState
}

/**
 * Состояние сессии выводится из access-токена: TokenAuthenticator чистит
 * хранилище, когда refresh не принят, поэтому разлогинивание получается само собой.
 */
@Singleton
class Session @Inject constructor(
    private val store: TokenStore,
    private val userApi: UserApi,
) {
    val state: Flow<SessionState> = store.accessFlow.mapLatest { access ->
        if (access == null) {
            SessionState.LoggedOut
        } else {
            profileOrLogout()
        }
    }

    suspend fun logout() = store.clear()

    private suspend fun profileOrLogout(): SessionState =
        try {
            SessionState.LoggedIn(userApi.me())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 401/429/офлайн — общего тут нет: токен живёт в сторе, и при следующей
            // его смене профиль догрузится. Экран входа при этом остаётся рабочим.
            if (e is HttpException && e.code() == 401) store.clear()
            SessionState.LoggedOut
        }
}
