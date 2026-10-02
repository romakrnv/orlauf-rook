package com.orlauf.rook

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit

const val ANIM_MS = 300

/**
 * Кнопка, у которой смена цвета (активна/неактивна, Start/Power off) и текста идёт плавно.
 * Стандартная Button переключает цвета мгновенно, поэтому цвета анимируем сами и отдаём
 * одинаковыми для обоих состояний.
 */
@Composable
fun SmoothButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val container by animateColorAsState(
        if (enabled) containerColor else onSurface.copy(alpha = 0.12f),
        tween(ANIM_MS),
        label = "container",
    )
    val content by animateColorAsState(
        if (enabled) contentColor else onSurface.copy(alpha = 0.38f),
        tween(ANIM_MS),
        label = "content",
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container,
            disabledContentColor = content,
        ),
        modifier = modifier,
    ) {
        Crossfade(targetState = text, animationSpec = tween(ANIM_MS), label = "text") { t ->
            Text(t, fontSize = fontSize)
        }
    }
}
