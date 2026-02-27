package com.familymovies.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familymovies.app.data.firestore.FirestoreRepository
import com.familymovies.app.data.model.Movie
import com.familymovies.app.data.player.PlayTokenRepository
import com.familymovies.app.data.player.PlayTokenResult
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Ready(
        val email: String,
        val movies: List<Movie>,
        val catalogToken: String,
        val baseUrl: String
    ) : HomeUiState()
    object NotAllowed : HomeUiState()
    data class Error(val message: String) : HomeUiState()
    data class PlayReady(
        val readyState: Ready,
        val movieId: String,
        val playToken: PlayTokenResult
    ) : HomeUiState()
}

class HomeViewModel : ViewModel() {

    private val firestoreRepository = FirestoreRepository()
    private val playTokenRepository = PlayTokenRepository()

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState

    private val _selectedCategory = MutableStateFlow("Todas")
    val selectedCategory: StateFlow<String> = _selectedCategory

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    init {
        loadAll()
    }

    private fun loadAll(refreshing: Boolean = false) {
        val email = Firebase.auth.currentUser?.email ?: run {
            _uiState.value = HomeUiState.NotAllowed
            return
        }
        if (!refreshing) _uiState.value = HomeUiState.Loading

        viewModelScope.launch {
            val allowedDeferred = async { firestoreRepository.isUserAllowed(email) }
            val moviesDeferred = async { firestoreRepository.getMovies() }
            val catalogTokenDeferred = async { playTokenRepository.getCatalogToken() }

            val allowed = allowedDeferred.await()
            if (!allowed) {
                _isRefreshing.value = false
                _uiState.value = HomeUiState.NotAllowed
                return@launch
            }

            val movies = try {
                moviesDeferred.await()
            } catch (e: Exception) {
                _isRefreshing.value = false
                _uiState.value = HomeUiState.Error("Error cargando películas: ${e.message}")
                return@launch
            }

            val catalogResult = catalogTokenDeferred.await()
            _isRefreshing.value = false

            catalogResult.fold(
                onSuccess = { catalog ->
                    _uiState.value = HomeUiState.Ready(
                        email = email,
                        movies = movies,
                        catalogToken = catalog.token,
                        baseUrl = catalog.baseUrl
                    )
                },
                onFailure = { e ->
                    _uiState.value = HomeUiState.Error(e.message ?: "Error al cargar catálogo")
                }
            )
        }
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        loadAll(refreshing = true)
    }

    fun retry() = loadAll()

    fun resetToReady() {
        currentReadyState()?.let { _uiState.value = it }
    }

    fun currentReadyState(): HomeUiState.Ready? =
        when (val s = _uiState.value) {
            is HomeUiState.Ready -> s
            is HomeUiState.PlayReady -> s.readyState
            else -> null
        }
}
