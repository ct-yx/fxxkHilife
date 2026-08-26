package com.freebuds.controller.ui.foundation.surface

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LightFieldContractTest {
    @Test
    fun pointerLightUsesRootPositionAndViewportRadius() {
        val light = LightFieldState()

        light.updateFromPointer(
            positionInRoot = Offset(120f, 240f),
            viewport = IntSize(1080, 2400),
            isPressed = true,
        )

        assertEquals(Offset(120f, 240f), light.positionInRoot)
        assertEquals(2400f, light.radiusPx, 0.001f)
        assertEquals(0.86f, light.intensity, 0.001f)
        assertTrue(light.pressed)
    }

    @Test
    fun releaseKeepsTheLastLightPositionAndReducesIntensity() {
        val light = LightFieldState()
        light.updateFromPointer(Offset(40f, 80f), IntSize(400, 800), true)

        light.release()

        assertEquals(Offset(40f, 80f), light.positionInRoot)
        assertEquals(0.30f, light.intensity, 0.001f)
        assertFalse(light.pressed)
    }
}
