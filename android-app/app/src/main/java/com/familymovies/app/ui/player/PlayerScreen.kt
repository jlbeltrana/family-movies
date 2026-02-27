package com.familymovies.app.ui.player

import android.app.Activity
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
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
private const val SKIP_MS = 10_000L
private const val CONTROLS_HIDE_DELAY_MS = 3_000L

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
    var speedIndex by remember { mutableIntStateOf(1) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var isBuffering by remember { mutableStateOf(true) }

    // Playback state
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableLongStateOf(0L) }

    // Controls visibility & lock
    var controlsVisible by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }

    // Skip feedback
    var skipVisible by remember { mutableStateOf(false) }
    var skipIsLeft by remember { mutableStateOf(true) }

    // Counters to restart LaunchedEffects
    var autoHideKey by remember { mutableIntStateOf(0) }
    var hideSkipKey by remember { mutableIntStateOf(0) }

    // Auto-hide controls after 3 s of inactivity
    LaunchedEffect(autoHideKey) {
        delay(CONTROLS_HIDE_DELAY_MS)
        if (!isLocked && !isSeeking) controlsVisible = false
    }

    // Auto-hide skip feedback after 800 ms
    LaunchedEffect(hideSkipKey) {
        delay(800)
        skipVisible = false
    }

    // Immersive full-screen
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val ctrl = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        ctrl?.apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose { ctrl?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    // Load saved progress
    LaunchedEffect(movieId) {
        val position = firestoreRepository.getProgress(movieId)
        if (position > RESUME_THRESHOLD_MS) {
            savedPosition = position
            showResumeDialog = true
        }
    }

    // Build ExoPlayer with JWT header on every request
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

    // Player state listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    duration = exoPlayer.duration.takeIf { it > 0 } ?: 0L
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                playerError = "[${error.errorCode}] ${error.message}\nCausa: ${error.cause?.message}"
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // Poll current position every 500 ms
    LaunchedEffect(exoPlayer) {
        while (true) {
            if (!isSeeking) {
                currentPosition = exoPlayer.currentPosition
                if (duration <= 0L) duration = exoPlayer.duration.takeIf { it > 0 } ?: 0L
            }
            delay(500)
        }
    }

    // Save progress every 15 s
    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(15_000)
            if (exoPlayer.isPlaying) {
                firestoreRepository.saveProgress(movieId, exoPlayer.currentPosition)
            }
        }
    }

    // Save progress and release on exit
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
        // ── Video (no built-in controls) ──────────────────────────────────
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── Transparent gesture capture layer ─────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isLocked) {
                    detectTapGestures(
                        onTap = {
                            if (!isLocked) {
                                controlsVisible = !controlsVisible
                                if (controlsVisible) autoHideKey++
                            }
                        },
                        onDoubleTap = { offset ->
                            if (!isLocked) {
                                if (offset.x < size.width / 2f) {
                                    exoPlayer.seekTo(maxOf(0L, exoPlayer.currentPosition - SKIP_MS))
                                    skipIsLeft = true
                                } else {
                                    val dur = exoPlayer.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                                    exoPlayer.seekTo(minOf(dur, exoPlayer.currentPosition + SKIP_MS))
                                    skipIsLeft = false
                                }
                                skipVisible = true
                                hideSkipKey++
                                controlsVisible = true
                                autoHideKey++
                            }
                        }
                    )
                }
        )

        // ── Skip feedback: −10 s (left) ───────────────────────────────────
        AnimatedVisibility(
            visible = skipVisible && skipIsLeft,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Box(
                modifier = Modifier
                    .padding(start = 40.dp)
                    .background(Color.White.copy(alpha = 0.25f), CircleShape)
                    .size(80.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("−10s", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }

        // ── Skip feedback: +10 s (right) ──────────────────────────────────
        AnimatedVisibility(
            visible = skipVisible && !skipIsLeft,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Box(
                modifier = Modifier
                    .padding(end = 40.dp)
                    .background(Color.White.copy(alpha = 0.25f), CircleShape)
                    .size(80.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("+10s", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }

        // ── Buffering indicator ───────────────────────────────────────────
        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Purple,
                strokeWidth = 3.dp
            )
        }

        // ── Playback error overlay ────────────────────────────────────────
        playerError?.let { err ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "Error de reproducción:\n$err",
                    color = Color(0xFFFF6B6B),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // ── Custom controls (fade in/out) ─────────────────────────────────
        AnimatedVisibility(
            visible = controlsVisible && !isLocked,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {

                // Top gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent),
                                endY = 200f
                            )
                        )
                        .padding(top = 8.dp, bottom = 64.dp)
                )

                // Back button (top-left)
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

                // Speed + Lock (top-right)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TextButton(
                        onClick = {
                            speedIndex = (speedIndex + 1) % SPEEDS.size
                            exoPlayer.setPlaybackSpeed(SPEEDS[speedIndex])
                            autoHideKey++
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

                    TextButton(
                        onClick = {
                            isLocked = true
                            controlsVisible = false
                        },
                        modifier = Modifier.background(
                            color = Color.White.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(20.dp)
                        )
                    ) {
                        Text("🔒", fontSize = 16.sp)
                    }
                }

                // Bottom controls: seek bar + play/pause + time
                val sliderMax = maxOf(1f, duration.toFloat())
                val sliderValue = if (isSeeking) seekPosition.toFloat()
                else currentPosition.toFloat().coerceIn(0f, sliderMax)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                            )
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Slider(
                        value = sliderValue,
                        valueRange = 0f..sliderMax,
                        onValueChange = { v ->
                            isSeeking = true
                            seekPosition = v.toLong()
                        },
                        onValueChangeFinished = {
                            exoPlayer.seekTo(seekPosition)
                            isSeeking = false
                            autoHideKey++
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Purple,
                            activeTrackColor = Purple,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatPosition(if (isSeeking) seekPosition else currentPosition),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = {
                                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                autoHideKey++
                            }
                        ) {
                            Text(
                                text = if (isPlaying) "⏸" else "▶",
                                fontSize = 26.sp,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = formatPosition(duration),
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }

        // ── Lock indicator (always visible when locked) ───────────────────
        if (isLocked) {
            TextButton(
                onClick = {
                    isLocked = false
                    controlsVisible = true
                    autoHideKey++
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color(0xCCB71C1C), RoundedCornerShape(20.dp))
            ) {
                Text(
                    text = "🔓 Desbloquear",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        // ── Resume dialog ─────────────────────────────────────────────────
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
