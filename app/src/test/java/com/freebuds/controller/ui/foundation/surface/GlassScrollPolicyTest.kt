package com.freebuds.controller.ui.foundation.surface

import com.freebuds.controller.ui.UiDisplayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassScrollPolicyTest {
    @Test
    fun listSurfaceNeverAttachesCapturedEffect() {
        assertEquals(
            SurfaceRenderMode.TintOnly,
            resolveScrollAwareRenderer(
                resolved = SurfaceRenderMode.HazeGlass,
                scrollableContent = true,
                isScrolling = false,
            ),
        )
        assertEquals(
            SurfaceRenderMode.TintOnly,
            resolveScrollAwareRenderer(
                resolved = SurfaceRenderMode.HazeBlur,
                scrollableContent = true,
                isScrolling = false,
            ),
        )
    }

    @Test
    fun staticSurfaceKeepsGlassUntilDragStarts() {
        assertEquals(
            SurfaceRenderMode.HazeGlass,
            resolveScrollAwareRenderer(
                resolved = SurfaceRenderMode.HazeGlass,
                scrollableContent = false,
                isScrolling = false,
            ),
        )
        assertEquals(
            SurfaceRenderMode.TintOnly,
            resolveScrollAwareRenderer(
                resolved = SurfaceRenderMode.HazeGlass,
                scrollableContent = false,
                isScrolling = true,
            ),
        )
    }

    @Test
    fun nonEffectRendererIsUnchanged() {
        assertEquals(
            SurfaceRenderMode.TintOnly,
            resolveScrollAwareRenderer(
                resolved = SurfaceRenderMode.TintOnly,
                scrollableContent = true,
                isScrolling = true,
            ),
        )
        assertEquals(
            SurfaceRenderMode.Material3,
            resolveScrollAwareRenderer(
                resolved = SurfaceRenderMode.Material3,
                scrollableContent = false,
                isScrolling = true,
            ),
        )
    }

    @Test
    fun scrollableViewportUsesOneStableEffectOnlyWhenInputIsReady() {
        assertTrue(
            shouldUseScrollableViewportEffect(
                displayMode = UiDisplayMode.LIQUID_GLASS,
                sourceAttached = true,
                hardwareAccelerated = true,
                apiLevel = 35,
            ),
        )
        assertFalse(
            shouldUseScrollableViewportEffect(
                displayMode = UiDisplayMode.CLASSIC,
                sourceAttached = true,
                hardwareAccelerated = true,
                apiLevel = 35,
            ),
        )
        assertFalse(
            shouldUseScrollableViewportEffect(
                displayMode = UiDisplayMode.LIQUID_GLASS,
                sourceAttached = false,
                hardwareAccelerated = true,
                apiLevel = 35,
            ),
        )
        assertFalse(
            shouldUseScrollableViewportEffect(
                displayMode = UiDisplayMode.LIQUID_GLASS,
                sourceAttached = true,
                hardwareAccelerated = true,
                apiLevel = 32,
            ),
        )
    }
}
