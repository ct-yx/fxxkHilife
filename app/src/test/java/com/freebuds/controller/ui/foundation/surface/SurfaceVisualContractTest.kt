package com.freebuds.controller.ui.foundation.surface

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SurfaceVisualContractTest {
    @Test
    fun surfaceRolesKeepTheVisualHierarchy() {
        assertEquals(28f, SurfaceRole.Hero.defaultCornerRadius().value, 0.001f)
        assertEquals(26f, SurfaceRole.FeatureCard.defaultCornerRadius().value, 0.001f)
        assertEquals(22f, SurfaceRole.StandardCard.defaultCornerRadius().value, 0.001f)
        assertEquals(20f, SurfaceRole.CompactRow.defaultCornerRadius().value, 0.001f)
    }

    @Test
    fun glassProfilesKeepAWideEdgeAndProfileSpecificDistortion() {
        assertTrue(GlassProfile.Compact.visual().edgeBand.value >= 9f)
        assertTrue(GlassProfile.Feature.visual().edgeBand.value >= 12f)
        assertTrue(GlassProfile.TopBar.visual().edgeBand.value >= 16f)
        assertEquals(0f, GlassProfile.Standard.visual().distortion, 0.001f)
        assertTrue(GlassProfile.TopBar.visual().distortion > 0f)
    }

    @Test
    fun surfaceRolesMapToTheExpectedGlassProfiles() {
        assertEquals(GlassProfile.Hero, SurfaceRole.Hero.glassProfile())
        assertEquals(GlassProfile.Feature, SurfaceRole.FeatureCard.glassProfile())
        assertEquals(GlassProfile.Standard, SurfaceRole.StandardCard.glassProfile())
        assertEquals(GlassProfile.Compact, SurfaceRole.CompactRow.glassProfile())
        assertEquals(GlassProfile.TopBar, SurfaceRole.AppBar.glassProfile())
    }
}
