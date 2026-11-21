package com.iptvhero.pro.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.iptvhero.pro.ui.theme.GoldFocus

fun Modifier.heroFocusable(
    shape: Shape = RoundedCornerShape(12.dp),
    onClick: () -> Unit
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.03f else 1.0f, label = "focusScale")
    val interactionSource = remember { MutableInteractionSource() }

    this
        .scale(scale)
        .onFocusChanged { isFocused = it.isFocused }
        .border(
            width = if (isFocused) 2.5.dp else 1.dp,
            color = if (isFocused) GoldFocus else Color.White.copy(alpha = 0.10f),
            shape = shape
        )
        .onKeyEvent { event ->
            val key = event.key
            val isConfirmKey = key == Key.DirectionCenter ||
                key == Key.Enter ||
                key == Key.NumPadEnter
            if (isConfirmKey && event.type == KeyEventType.KeyUp) {
                onClick()
                true
            } else {
                false
            }
        }
        .focusable(interactionSource = interactionSource)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}
