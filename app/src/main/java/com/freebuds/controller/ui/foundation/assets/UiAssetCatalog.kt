package com.freebuds.controller.ui.foundation.assets

import androidx.annotation.DrawableRes
import com.freebuds.controller.R

/** Semantic asset lookup. Keeping resource IDs here prevents page-specific drawable branching. */
object UiAssetCatalog {
    enum class AncVisual { Off, Cancellation, Awareness }
    enum class DeviceVisual { EarbudCase, Tile }

    @DrawableRes
    fun anc(mode: AncVisual): Int = when (mode) {
        AncVisual.Off -> R.drawable.ic_anc_normal
        AncVisual.Cancellation -> R.drawable.ic_anc_cancellation
        AncVisual.Awareness -> R.drawable.ic_anc_awareness
    }

    @DrawableRes
    fun device(visual: DeviceVisual): Int = when (visual) {
        DeviceVisual.EarbudCase -> R.drawable.ic_earbuds_case
        DeviceVisual.Tile -> R.drawable.ic_tile
    }
}
