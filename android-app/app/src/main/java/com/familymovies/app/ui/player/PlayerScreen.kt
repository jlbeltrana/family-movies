package com.familymovies.app.ui.player

import android.app.Activity
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.familymovies.app.data.firestore.FirestoreRepository
import com.familymovies.app.ui.theme.Purple
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
private val SPEED_LABELS = listOf("0.75×", "1×", "1.25×", "1.5×", "2×")
private const val RESUME_THRESHOLD_MS = 30_000L

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    manifestUrl: String,
    token: String,
    movieId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val firestoreRepository = remember { FirestoreRepository() }

    var savedPosition by remember { mutableLongStateOf(0L) }
    var showResumeDialog by remember { mutableStateOf(false) }
    var speedIndex by remember { mutableIntStateOf(1) } // 1 = 1× por defecto

    // Pantalla completa inmersiva
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val ctrl = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        ctrl?.apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose { ctrl?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    // Cargar progreso guardado
    LaunchedEffect(movieId) {
        val position = firestoreRepository.getProgress(movieId)
        if (position > RESUME_THRESHOLD_MS) {
            savedPosition = position
            showResumeDialog = true
        }
    }

    // ExoPlayer
    val exoPlayer = remember(manifestUrl, token) {
        val factory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf("Authorization" to "Bearer $token"))
        val source = HlsMediaSource.Factory(factory)
            .createMediaSource(MediaItem.fromUri(Uri.parse(manifestUrl)))
        ExoPlayer.Builder(context).build().apply {
            setMediaSource(source)
            prepare()
            playWhenReady = true
        }
    }

    // Guardar progreso cada 15 segundos
    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(15_000)
            if (exoPlayer.isPlaying) {
                firestoreRepository.saveProgress(movieId, exoPlayer.currentPosition)
            }
        }
    }

    // Guardar al salir y liberar
    DisposableEffect(exoPlayer) {
        onDispose {
            val position = exoPlayer.currentPosition
            scope.launch { firestoreRepository.saveProgress(movieId, position) }
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Botón atrás
        TextButton(
            onClick = {
                scope.launch {
                    firestoreRepository.saveProgress(movieId, exoPlayer.currentPosition)
                    onBack()
                }
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Text("← Atrás", color = Color.White)
        }

        // Selector de velocidad
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TextButton(
                onClick = {
                    speedIndex = (speedIndex + 1) % SPEEDS.size
                    exoPlayer.setPlaybackSpeed(SPEEDS[speedIndex])
                },
                modifier = Modifier.background(
                    color = if (speedIndex != 1) Purple.copy(alpha = 0.8f)
                    else Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(20.dp)
                )
            ) {
                Text(
                    text = SPEED_LABELS[speedIndex],
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Diálogo de resume
        if (showResumeDialog) {
            AlertDialog(
                onDismissRequest = { showResumeDialog = false },
                title = { Text("¿Continuar viendo?") },
                text = { Text("Quedaste en ${formatPosition(savedPosition)}. ¿Continuar desde ahí?") },
                confirmButton = {
                    Button(
                        onClick = {
                            exoPlayer.seekTo(savedPosition)
                            showResumeDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Purple)
                    ) { Text("Continuar") }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showResumeDialog = false }) {
                        Text("Desde el inicio")
                    }
                },
                containerColor = Color(0xFF1A1A2E)
            )
        }
    }
}

private fun formatPosition(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}
