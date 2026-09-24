package com.example.messenger.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.messenger.data.Session
import com.example.messenger.data.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    private val session: Session,
) : ViewModel() {

    val state: StateFlow<SessionState> = session.state
        .catch { emit(SessionState.LoggedOut) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SessionState.Unknown)

    fun logout() {
        viewModelScope.launch { session.logout() }
    }
}
