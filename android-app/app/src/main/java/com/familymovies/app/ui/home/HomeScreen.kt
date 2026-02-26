package com.familymovies.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.familymovies.app.data.model.Movie
import com.familymovies.app.ui.theme.DarkBackground
import com.familymovies.app.ui.theme.Purple
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

@Composable
fun HomeScreen(
    onPlayVideo: (manifestUrl: String, token: String, movieId: String) -> Unit,
    onSignOut: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is HomeUiState.PlayReady -> {
                val ready = state.readyState
                val manifestUrl = "${state.playToken.baseUrl}/movies/${state.movieId}/master.m3u8"
                onPlayVideo(manifestUrl, state.playToken.token, state.movieId)
                viewModel.resetToReady()
            }
            is HomeUiState.NotAllowed -> {
                Firebase.auth.signOut()
                onSignOut()
            }
            else -> Unit
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        when (val state = uiState) {
            is HomeUiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Purple
                )
            }

            is HomeUiState.Ready -> {
                CatalogContent(
                    state = state,
                    onPlayMovie = { viewModel.playMovie(it) },
                    onSignOut = {
                        Firebase.auth.signOut()
                        onSignOut()
                    }
                )
            }

            is HomeUiState.PlayReady -> {
                // Sigue mostrando el catálogo mientras navega
                CatalogContent(
                    state = state.readyState,
                    onPlayMovie = {},
                    onSignOut = {}
                )
            }

            is HomeUiState.Error -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("😕", style = MaterialTheme.typography.displaySmall)
                    Text(
                        text = state.message,
                        color = Color(0xFFFF6B6B),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = { viewModel.retry() }) {
                        Text("Reintentar", color = Purple)
                    }
                }
            }

            else -> Unit
        }
    }
}

@Composable
private fun CatalogContent(
    state: HomeUiState.Ready,
    onPlayMovie: (movieId: String) -> Unit,
    onSignOut: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🎬 ", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Lucy Movies",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Purple
                    )
                )
            }
            TextButton(onClick = onSignOut) {
                Text("Salir", color = Color.White.copy(alpha = 0.5f))
            }
        }

        if (state.movies.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No hay películas aún",
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        } else {
            Text(
                text = "¿Qué vemos hoy? ✨",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.movies, key = { it.id }) { movie ->
                    MovieCard(
                        movie = movie,
                        catalogToken = state.catalogToken,
                        baseUrl = state.baseUrl,
                        onClick = { onPlayMovie(movie.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MovieCard(
    movie: Movie,
    catalogToken: String,
    baseUrl: String,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val posterUrl = "$baseUrl/${movie.posterPath}"

    Card(
        onClick = onClick,
        modifier = Modifier.aspectRatio(2f / 3f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Poster con JWT
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(posterUrl)
                    .addHeader("Authorization", "Bearer $catalogToken")
                    .crossfade(true)
                    .build(),
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Gradiente + título en la parte inferior
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xEE000000)),
                            startY = 0f,
                            endY = 200f
                        )
                    )
                    .padding(horizontal = 10.dp, vertical = 10.dp)
            ) {
                Text(
                    text = movie.title,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
