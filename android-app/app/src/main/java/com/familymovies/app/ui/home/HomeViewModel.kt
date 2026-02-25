package com.familymovies.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familymovies.app.data.firestore.FirestoreRepository
import com.familymovies.app.data.player.PlayTokenRepository
import com.familymovies.app.data.player.PlayTokenResult
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Ready(val email: String) : HomeUiState()
    object NotAllowed : HomeUiState()
    data class Error(val message: String) : HomeUiState()
    data class PlayReady(val email: String, val playToken: PlayTokenResult) : HomeUiState()
}

class HomeViewModel : ViewModel() {

    private val firestoreRepository = FirestoreRepository()
    private val playTokenRepository = PlayTokenRepository()

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        checkAllowlist()
    }

    private fun checkAllowlist() {
        val email = Firebase.auth.currentUser?.email ?: run {
            _uiState.value = HomeUiState.NotAllowed
            return
        }
        viewModelScope.launch {
            val allowed = firestoreRepository.isUserAllowed(email)
            _uiState.value = if (allowed) HomeUiState.Ready(email) else HomeUiState.NotAllowed
        }
    }

    fun playTest() {
        val email = Firebase.auth.currentUser?.email ?: return
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            playTokenRepository.getPlayToken("test").fold(
                onSuccess = { playToken ->
                    _uiState.value = HomeUiState.PlayReady(email, playToken)
                },
                onFailure = { e ->
                    _uiState.value = HomeUiState.Error(e.message ?: "Error al obtener token")
                }
            )
        }
    }

    fun resetToReady() {
        val email = Firebase.auth.currentUser?.email ?: return
        _uiState.value = HomeUiState.Ready(email)
    }
}
