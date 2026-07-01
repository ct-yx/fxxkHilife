package com.freebuds.controller.adapter.huawei

import android.bluetooth.BluetoothDevice
import com.freebuds.controller.bluetooth.*
import com.freebuds.controller.core.adapter.EarbudAdapter
import com.freebuds.controller.core.adapter.EarbudAdapterCallbacks
import com.freebuds.controller.protocol.HuaweiCapability
import com.freebuds.controller.protocol.HuaweiModel
import com.freebuds.controller.protocol.modelCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Adapter for the current OpenFreebuds-derived HUAWEI / HONOR SPP protocol stack.
 *
 * This is intentionally still backed by the legacy SppDriver and Huawei handlers. The purpose
 * of the adapter is to move vendor/model/handler selection out of DeviceRepository first, so
 * future vendor adapters can be added without making the repository branchier.
 */
object HuaweiOpenFreebudsAdapter : EarbudAdapter {
    override val id: String = "huawei_openfreebuds"
    override val displayName: String = "HUAWEI / HONOR (OpenFreebuds)"

    private val callbackScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun canHandle(device: BluetoothDevice): Boolean = isHuaweiOrHonorName(device.name)

    override fun registerHandlers(driver: SppDriver, deviceName: String, callbacks: EarbudAdapterCallbacks) {
        val model = detectModel(deviceName)
        val caps = modelCapabilities[model]?.toSet() ?: emptySet()
        fun has(c: HuaweiCapability) = caps.isEmpty() || c in caps

        driver.registerHandler(LogsHandler())
        if (has(HuaweiCapability.INFO)) driver.registerHandler(InfoHandler())
        if (has(HuaweiCapability.WEAR_DETECT)) driver.registerHandler(InEarHandler())
        if (has(HuaweiCapability.BATTERY)) {
            val bh = BatteryHandler(wTws = model?.hasTwsBattery ?: true)
            bh.setOnBatteryUpdate { callbackScope.launch { callbacks.onStateChanged() } }
            driver.registerHandler(bh)
        }
        if (has(HuaweiCapability.ANC_LEGACY)) driver.registerHandler(AncLegacyChangeHandler())
        if (has(HuaweiCapability.ANC)) driver.registerHandler(AncHandler())
        if (has(HuaweiCapability.ACTION_DOUBLE_TAP)) driver.registerHandler(DoubleTapHandler())
        if (has(HuaweiCapability.ACTION_TRIPLE_TAP)) driver.registerHandler(TripleTapHandler())
        if (has(HuaweiCapability.ACTION_LONG_TAP_SPLIT)) driver.registerHandler(LongTapHandler())
        if (has(HuaweiCapability.ACTION_SWIPE)) driver.registerHandler(SwipeGestureHandler())
        if (has(HuaweiCapability.ACTION_POWER_BUTTON)) driver.registerHandler(PowerButtonHandler())
        if (has(HuaweiCapability.AUTO_PAUSE)) driver.registerHandler(AutoPauseHandler())
        if (has(HuaweiCapability.LOW_LATENCY)) driver.registerHandler(LowLatencyHandler())
        if (has(HuaweiCapability.SOUND_QUALITY)) driver.registerHandler(SoundQualityHandler())
        if (has(HuaweiCapability.VOICE_LANGUAGE)) driver.registerHandler(VoiceLanguageHandler())
    }

    fun isHuaweiOrHonorName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val upper = name.uppercase()
        return HUAWEI_PREFIXES.any { upper.contains(it.uppercase()) }
    }

    fun detectModel(name: String): HuaweiModel? = when {
        name.contains("FreeBuds 7i", true)    -> HuaweiModel.BUDS_7I
        name.contains("FreeBuds 6i", true)    -> HuaweiModel.BUDS_6I
        name.contains("FreeBuds Pro 4", true) ||
        name.contains("FreeBuds Pro 3", true) ||
        name.contains("FreeClip", true)        -> HuaweiModel.BUDS_PRO_3
        name.contains("FreeBuds Pro 2", true)  -> HuaweiModel.BUDS_PRO_2
        name.contains("FreeBuds Pro", true)    -> HuaweiModel.BUDS_PRO
        name.contains("FreeBuds Studio", true) -> HuaweiModel.STUDIO
        name.contains("FreeBuds SE 4", true)   -> HuaweiModel.BUDS_SE_4
        name.contains("FreeBuds SE 2", true)   -> HuaweiModel.BUDS_SE_2
        name.contains("FreeBuds SE", true)     -> HuaweiModel.BUDS_SE
        name.contains("FreeBuds 5i", true)     -> HuaweiModel.BUDS_5I
        name.contains("FreeBuds 4i", true)     -> HuaweiModel.BUDS_4I
        name.contains("FreeLace Pro 2", true)  -> HuaweiModel.LACE_PRO_2
        name.contains("FreeLace Pro", true)    -> HuaweiModel.LACE_PRO
        name.contains("Earbuds", true)         -> HuaweiModel.BUDS_4I
        else -> null
    }

    private val HUAWEI_PREFIXES = listOf(
        "HUAWEI", "HONOR", "FreeBuds", "Freebuds", "freebuds", "华为", "荣耀", "Honor"
    )
}