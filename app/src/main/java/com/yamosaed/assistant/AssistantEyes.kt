package com.yamosaed.assistant

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AssistantEyes(listening: Boolean) {
    val transition = rememberInfiniteTransition(label = "assistant-eyes")
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = if (listening) 1.12f else 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (listening) 450 else 1400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eye-pulse"
    )
    Box(
        modifier = Modifier.size(158.dp)
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF3C72E8), Color(0xFF1A2D59), Color(0xFF10182A))
                ),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(68.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(2) {
                Box(
                    modifier = Modifier.size(width = 38.dp, height = 54.dp)
                        .scale(pulse)
                        .background(Color(0xFFEAF4FF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier.size(19.dp)
                            .background(Color(0xFF142A54), CircleShape)
                    )
                }
                if (it == 0) Box(Modifier.size(18.dp))
            }
        }
    }
}
