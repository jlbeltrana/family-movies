package com.familymovies.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin

/**
 * Fondo mágico con estrellas y destellos — estilo app infantil.
 * No requiere ninguna imagen externa.
 */
@Composable
fun FunBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        // Base: gradiente oscuro azul-morado
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF07041A),
                    Color(0xFF130A2E),
                    Color(0xFF0D0D1A)
                )
            )
        )

        // Círculos de brillo grandes (neblina de color)
        val glows = listOf(
            Triple(0.15f, 0.10f, Color(0xFFE040FB)),  // púrpura arriba-izquierda
            Triple(0.85f, 0.18f, Color(0xFFFF6B9D)),  // rosa arriba-derecha
            Triple(0.50f, 0.55f, Color(0xFF7C4DFF)),  // índigo centro
            Triple(0.10f, 0.80f, Color(0xFFFF6B9D)),  // rosa abajo-izquierda
            Triple(0.90f, 0.75f, Color(0xFFE040FB)),  // púrpura abajo-derecha
        )
        for ((xf, yf, color) in glows) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.18f), Color.Transparent),
                    center = Offset(size.width * xf, size.height * yf),
                    radius = size.minDimension * 0.45f
                ),
                radius = size.minDimension * 0.45f,
                center = Offset(size.width * xf, size.height * yf)
            )
        }

        // Estrellas pequeñas (puntos)
        val stars = listOf(
            0.05f to 0.05f, 0.18f to 0.03f, 0.33f to 0.08f, 0.50f to 0.02f,
            0.65f to 0.06f, 0.80f to 0.04f, 0.93f to 0.09f, 0.12f to 0.15f,
            0.28f to 0.20f, 0.45f to 0.13f, 0.60f to 0.18f, 0.75f to 0.11f,
            0.88f to 0.22f, 0.03f to 0.30f, 0.22f to 0.35f, 0.38f to 0.28f,
            0.55f to 0.32f, 0.70f to 0.25f, 0.92f to 0.38f, 0.08f to 0.48f,
            0.20f to 0.55f, 0.42f to 0.45f, 0.58f to 0.50f, 0.78f to 0.43f,
            0.95f to 0.52f, 0.15f to 0.65f, 0.32f to 0.70f, 0.52f to 0.62f,
            0.68f to 0.68f, 0.85f to 0.60f, 0.07f to 0.78f, 0.25f to 0.82f,
            0.44f to 0.75f, 0.62f to 0.80f, 0.80f to 0.73f, 0.96f to 0.85f,
            0.13f to 0.92f, 0.35f to 0.88f, 0.55f to 0.93f, 0.75f to 0.90f,
        )
        for ((xf, yf) in stars) {
            val r = (2f + ((xf * 17 + yf * 31) % 3)) * density
            val alpha = 0.4f + ((xf * 13 + yf * 7) % 6) * 0.1f
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = r,
                center = Offset(size.width * xf, size.height * yf)
            )
        }

        // Destellos en forma de estrella de 4 puntas
        val sparkles = listOf(
            Triple(0.08f, 0.22f, 10f),
            Triple(0.72f, 0.08f, 14f),
            Triple(0.90f, 0.45f, 10f),
            Triple(0.30f, 0.60f, 12f),
            Triple(0.55f, 0.15f, 8f),
            Triple(0.18f, 0.70f, 11f),
            Triple(0.85f, 0.88f, 13f),
            Triple(0.45f, 0.85f, 9f),
        )
        for ((xf, yf, sizeDp) in sparkles) {
            drawSparkle(
                center = Offset(size.width * xf, size.height * yf),
                sizePx = sizeDp * density,
                color = if ((xf * 10).toInt() % 2 == 0)
                    Color(0xFFFFD54F).copy(alpha = 0.7f)
                else
                    Color(0xFFFF80AB).copy(alpha = 0.65f)
            )
        }
    }
}

private fun DrawScope.drawSparkle(center: Offset, sizePx: Float, color: Color) {
    val path = Path()
    val arms = 4
    val outerR = sizePx
    val innerR = sizePx * 0.3f
    for (i in 0 until arms * 2) {
        val angle = (Math.PI / arms * i - Math.PI / 2).toFloat()
        val r = if (i % 2 == 0) outerR else innerR
        val x = center.x + r * cos(angle)
        val y = center.y + r * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color = color)
    // Halo suave alrededor del destello
    drawCircle(
        color = color.copy(alpha = 0.25f),
        radius = sizePx * 1.4f,
        center = center
    )
}
