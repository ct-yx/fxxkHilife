package com.freebuds.controller.ui.foundation.surface

import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperGlassContractTest {
    @Test
    fun cropTransformUsesContentScaleCropGeometry() {
        val transform = calculateWallpaperTransform(
            sourceWidth = 2000,
            sourceHeight = 1000,
            viewport = IntSize(1000, 1000),
        )

        requireNotNull(transform)
        assertEquals(1f, transform.scale, 0.0001f)
        assertEquals(-500f, transform.offset.x, 0.0001f)
        assertEquals(0f, transform.offset.y, 0.0001f)
    }

    @Test
    fun textureSizeIsDownscaledAndBounded() {
        assertEquals(IntSize(250, 125), calculateWallpaperTextureSize(2000, 1000))
        assertEquals(IntSize(320, 320), calculateWallpaperTextureSize(4000, 4000))
        assertTrue(calculateWallpaperTextureSize(320, 180).width >= 128)
    }

    @Test
    fun invalidTransformInputsReturnNoSamplingGeometry() {
        assertEquals(null, calculateWallpaperTransform(0, 1000, IntSize(1000, 1000)))
        assertEquals(null, calculateWallpaperTransform(1000, 1000, IntSize.Zero))
    }
}
