package com.example.messenger.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "auth")

@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    private val ACCESS = stringPreferencesKey("access")
    private val REFRESH = stringPreferencesKey("refresh")

    val accessFlow: Flow<String?> = ctx.dataStore.data.map { it[ACCESS] }

    suspend fun access(): String? = ctx.dataStore.data.first()[ACCESS]
    suspend fun refresh(): String? = ctx.dataStore.data.first()[REFRESH]

    suspend fun save(access: String, refresh: String) {
        ctx.dataStore.edit {
            it[ACCESS] = access
            it[REFRESH] = refresh
        }
    }

    suspend fun updateAccess(access: String) {
        ctx.dataStore.edit { it[ACCESS] = access }
    }

    suspend fun clear() {
        ctx.dataStore.edit { it.clear() }
    }

    // OkHttp-перехватчик не умеет suspend
    fun accessBlocking(): String? = runBlocking { access() }
    fun refreshBlocking(): String? = runBlocking { refresh() }
}
