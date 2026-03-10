package com.familymovies.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.familymovies.app.data.player.PlayTokenRepository
import com.familymovies.app.ui.home.HomeUiState
import com.familymovies.app.ui.home.HomeViewModel
import com.familymovies.app.ui.theme.DarkBackground
import com.familymovies.app.ui.theme.Purple
import kotlinx.coroutines.launch

@Composable
fun MovieDetailScreen(
    movieId: String,
    homeViewModel: HomeViewModel,
    onPlay: (manifestUrl: String, token: String, movieId: String) -> Unit,
    onBack: () -> Unit
) {
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val ready = when (val s = uiState) {
        is HomeUiState.Ready -> s
        is HomeUiState.PlayReady -> s.readyState
        else -> null
    }
    val movie = ready?.movies?.find { it.id == movieId }

    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val playTokenRepository = remember { PlayTokenRepository() }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        if (movie == null) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Purple
            )
        } else if (isLandscape) {
            // Layout horizontal: poster izquierda, info derecha
            Row(modifier = Modifier.fillMaxSize()) {
                // Poster
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(2f / 3f)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data("${ready.baseUrl}/${movie.posterPath}")
                            .addHeader("Authorization", "Bearer ${ready.catalogToken}")
                            .crossfade(true)
                            .build(),
                        contentDescription = movie.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(80.dp)
                            .align(Alignment.CenterEnd)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color.Transparent, DarkBackground)
                                )
                            )
                    )
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(8.dp)
                    ) {
                        Text(
                            "← Atrás",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }

                // Info + botón
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 32.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    MovieInfo(movie = movie, isLoading = isLoading, error = error, onPlay = {
                        if (!isLoading) {
                            error = null
                            isLoading = true
                            scope.launch {
                                playTokenRepository.getPlayToken(movieId).fold(
                                    onSuccess = { result ->
                                        val manifestUrl = "${result.baseUrl}/movies/$movieId/master.m3u8"
                                        isLoading = false
                                        onPlay(manifestUrl, result.token, movieId)
                                    },
                                    onFailure = { e ->
                                        isLoading = false
                                        error = e.message ?: "Error al obtener el token"
                                    }
                                )
                            }
                        }
                    })
                }
            }
        } else {
            // Layout vertical (portrait)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data("${ready.baseUrl}/${movie.posterPath}")
                            .addHeader("Authorization", "Bearer ${ready.catalogToken}")
                            .crossfade(true)
                            .build(),
                        contentDescription = movie.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, DarkBackground)
                                )
                            )
                    )
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(8.dp)
                    ) {
                        Text(
                            "← Atrás",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    MovieInfo(movie = movie, isLoading = isLoading, error = error, onPlay = {
                        if (!isLoading) {
                            error = null
                            isLoading = true
                            scope.launch {
                                playTokenRepository.getPlayToken(movieId).fold(
                                    onSuccess = { result ->
                                        val manifestUrl = "${result.baseUrl}/movies/$movieId/master.m3u8"
                                        isLoading = false
                                        onPlay(manifestUrl, result.token, movieId)
                                    },
                                    onFailure = { e ->
                                        isLoading = false
                                        error = e.message ?: "Error al obtener el token"
                                    }
                                )
                            }
                        }
                    })
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun MovieInfo(
    movie: com.familymovies.app.data.model.Movie,
    isLoading: Boolean,
    error: String?,
    onPlay: () -> Unit
) {
    Text(
        text = movie.title,
        style = MaterialTheme.typography.headlineMedium.copy(
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    )

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (movie.year > 0) InfoChip(text = movie.year.toString())
        if (movie.duration > 0) InfoChip(text = "${movie.duration} min")
        if (movie.category.isNotBlank()) InfoChip(text = movie.category.replaceFirstChar { it.uppercase() })
    }

    Spacer(modifier = Modifier.height(32.dp))

    Button(
        onClick = onPlay,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Purple)
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Text("Cargando...", style = MaterialTheme.typography.bodyLarge)
        } else {
            Text("▶  Reproducir", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
        }
    }

    if (error != null) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = error, color = Color(0xFFFF6B6B), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun InfoChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1A2E))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelMedium
        )
    }
}
