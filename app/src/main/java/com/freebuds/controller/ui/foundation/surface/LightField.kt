package com.freebuds.controller.ui.foundation.surface

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.math.max

/**
 * 页面级虚拟光源。它只改变 shader uniform，不改变布局，也不参与业务状态。
 */
@Stable
internal class LightFieldState {
    var positionInRoot by mutableStateOf(Offset.Unspecified)
        private set

    var intensity by mutableFloatStateOf(0.30f)
        private set

    var radiusPx by mutableFloatStateOf(480f)
        private set

    var pressed by mutableStateOf(false)
        private set

    fun updateFromPointer(positionInRoot: Offset, viewport: IntSize, isPressed: Boolean) {
        this.positionInRoot = positionInRoot
        this.radiusPx = max(max(viewport.width, viewport.height).toFloat(), 360f)
        this.intensity = if (isPressed) 0.86f else 0.42f
        this.pressed = isPressed
    }

    fun release() {
        intensity = 0.30f
        pressed = false
    }
}
