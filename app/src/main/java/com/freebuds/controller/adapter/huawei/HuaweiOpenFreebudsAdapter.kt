package com.freebuds.controller.adapter.huawei

import android.bluetooth.BluetoothDevice
import com.freebuds.controller.bluetooth.*
import com.freebuds.controller.core.adapter.EarbudAdapter
import com.freebuds.controller.core.adapter.EarbudAdapterCallbacks
import com.freebuds.controller.core.capability.EarbudCapability
import com.freebuds.controller.core.state.EarbudState
import com.freebuds.controller.core.transport.EndpointSource
import com.freebuds.controller.core.transport.RfcommTransportConfig
import com.freebuds.controller.core.transport.RfcommTransportConfigProvider
import com.freebuds.controller.data.DeviceProps
import com.freebuds.controller.data.EarbudStateMapper
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
object HuaweiOpenFreebudsAdapter : EarbudAdapter, RfcommTransportConfigProvider {
    override val id: String = "huawei_openfreebuds"
    override val displayName: String = "HUAWEI / HONOR (OpenFreebuds)"

    private val callbackScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun canHandle(device: BluetoothDevice): Boolean = isKnownModelName(device.name)

    override fun rfcommTransportConfig(deviceName: String?): RfcommTransportConfig {
        val model = detectModel(deviceName.orEmpty())
        return RfcommTransportConfig(
            channel = model?.sppPort ?: RfcommTransportConfig.DEFAULT_CHANNEL,
            source = if (model == null) EndpointSource.CompatibilityFallback
            else EndpointSource.VerifiedModelConfig,
        )
    }

    override fun capabilities(deviceName: String): Set<EarbudCapability> =
        detectModel(deviceName)?.let { model ->
            modelCapabilities[model].orEmpty().mapNotNull { it.toGenericCapability() }.toSet()
        }.orEmpty()

    override fun registerHandlers(driver: SppDriver, deviceName: String, callbacks: EarbudAdapterCallbacks) {
        val model = detectModel(deviceName)
        val caps = modelCapabilities[model]?.toSet().orEmpty()
        // Unknown models use a conservative profile. An empty table is not evidence that every
        // command is supported; registering all handlers made the UI advertise unverified writes.
        fun has(c: HuaweiCapability) = c in caps

        if (has(HuaweiCapability.LOGS)) driver.registerHandler(LogsHandler())
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
        if (has(HuaweiCapability.ACTION_LONG_TAP) || has(HuaweiCapability.ACTION_LONG_TAP_SPLIT)) {
            driver.registerHandler(LongTapHandler())
        }
        if (has(HuaweiCapability.ACTION_SWIPE)) driver.registerHandler(SwipeGestureHandler())
        if (has(HuaweiCapability.ACTION_POWER_BUTTON)) driver.registerHandler(PowerButtonHandler())
        if (has(HuaweiCapability.AUTO_PAUSE)) driver.registerHandler(AutoPauseHandler())
        if (has(HuaweiCapability.LOW_LATENCY)) driver.registerHandler(LowLatencyHandler())
        if (has(HuaweiCapability.SOUND_QUALITY)) driver.registerHandler(SoundQualityHandler())
        if (has(HuaweiCapability.EQ_PRESET) || has(HuaweiCapability.EQ_CUSTOM)) {
            driver.registerHandler(
                EqualizerPresetHandler(
                    wCustom = has(HuaweiCapability.EQ_CUSTOM),
                    wFakeBuiltIn = has(HuaweiCapability.EQ_FAKE_BUILTIN),
                )
            )
        }
        if (has(HuaweiCapability.DUAL_CONNECT)) {
            driver.registerHandler(DualConnectHandler(wAutoConnect = has(HuaweiCapability.DUAL_CONNECT_AUTO)))
        }
        if (has(HuaweiCapability.VOICE_LANGUAGE)) driver.registerHandler(VoiceLanguageHandler())
    }

    override suspend fun mapState(
        driver: SppDriver,
        pendingHandlers: Collection<String>,
        connectedSince: Long?,
    ): DeviceProps = EarbudStateMapper.fromHuaweiDriver(driver, pendingHandlers, connectedSince)

    override suspend fun mapEarbudState(
        driver: SppDriver,
        deviceName: String,
        pendingHandlers: Collection<String>,
        connectedSince: Long?,
    ): EarbudState = EarbudStateMapper.fromDeviceProps(
        props = mapState(driver, pendingHandlers, connectedSince),
        connectedSince = connectedSince,
    )

    fun isHuaweiOrHonorName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val upper = name.uppercase()
        return HUAWEI_PREFIXES.any { upper.contains(it.uppercase()) }
    }

    /** A vendor-looking name is not enough to select a protocol/capability profile. */
    fun isKnownModelName(name: String?): Boolean = detectModel(name.orEmpty()) != null

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
        name.contains("HONOR Earbuds 2 SE", true) ||
        name.contains("HONOR Earbuds 2 Lite", true) ||
        name.contains("HONOR Earbuds 2", true)    -> HuaweiModel.BUDS_4I
        else -> null
    }

    private val HUAWEI_PREFIXES = listOf(
        "HUAWEI", "HONOR", "FreeBuds", "Freebuds", "freebuds", "华为", "荣耀", "Honor"
    )

    private fun HuaweiCapability.toGenericCapability(): EarbudCapability? = when (this) {
        HuaweiCapability.INFO -> EarbudCapability.DEVICE_INFO
        HuaweiCapability.BATTERY,
        HuaweiCapability.BATTERY_TWS -> EarbudCapability.BATTERY
        HuaweiCapability.ANC,
        HuaweiCapability.ANC_LEGACY -> EarbudCapability.ANC
        HuaweiCapability.ANC_LEVEL -> EarbudCapability.ANC_LEVEL
        HuaweiCapability.ANC_DYNAMIC -> EarbudCapability.ANC_DYNAMIC
        HuaweiCapability.AUTO_PAUSE -> EarbudCapability.AUTO_PAUSE
        HuaweiCapability.LOW_LATENCY -> EarbudCapability.LOW_LATENCY
        HuaweiCapability.SOUND_QUALITY -> EarbudCapability.SOUND_QUALITY
        HuaweiCapability.EQ_PRESET,
        HuaweiCapability.EQ_FAKE_BUILTIN -> EarbudCapability.EQUALIZER
        HuaweiCapability.EQ_CUSTOM -> EarbudCapability.CUSTOM_EQUALIZER
        HuaweiCapability.DUAL_CONNECT,
        HuaweiCapability.DUAL_CONNECT_AUTO -> EarbudCapability.DUAL_CONNECT
        HuaweiCapability.WEAR_DETECT,
        HuaweiCapability.IN_EAR -> EarbudCapability.WEAR_DETECTION
        HuaweiCapability.ACTION_DOUBLE_TAP,
        HuaweiCapability.ACTION_DOUBLE_TAP_IN_CALL,
        HuaweiCapability.ACTION_TRIPLE_TAP,
        HuaweiCapability.ACTION_LONG_TAP,
        HuaweiCapability.ACTION_LONG_TAP_SPLIT,
        HuaweiCapability.ACTION_LONG_TAP_SPLIT_ANC,
        HuaweiCapability.ACTION_LONG_TAP_RIGHT,
        HuaweiCapability.ACTION_LONG_TAP_EXTRA,
        HuaweiCapability.ACTION_LONG_TAP_IN_CALL,
        HuaweiCapability.ACTION_SWIPE,
        HuaweiCapability.ACTION_POWER_BUTTON -> EarbudCapability.GESTURES
        HuaweiCapability.VOICE_LANGUAGE -> EarbudCapability.VOICE_LANGUAGE
        HuaweiCapability.LOGS -> EarbudCapability.LOGS
        HuaweiCapability.VOICE_BOOST -> EarbudCapability.VOICE_BOOST
    }
}
