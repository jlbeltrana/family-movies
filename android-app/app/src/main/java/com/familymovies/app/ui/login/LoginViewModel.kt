package com.familymovies.app.ui.login

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familymovies.app.data.auth.AuthRepository
import com.familymovies.app.data.firestore.FirestoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    object Success : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

class LoginViewModel : ViewModel() {

    private val authRepository = AuthRepository()
    private val firestoreRepository = FirestoreRepository()

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState

    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            authRepository.signInWithGoogle(context).fold(
                onSuccess = { email ->
                    val allowed = firestoreRepository.isUserAllowed(email)
                    if (allowed) {
                        _uiState.value = LoginUiState.Success
                    } else {
                        authRepository.signOut()
                        _uiState.value = LoginUiState.Error("No tienes permiso para acceder")
                    }
                },
                onFailure = { e ->
                    _uiState.value = LoginUiState.Error(e.message ?: "Error desconocido")
                }
            )
        }
    }
}
