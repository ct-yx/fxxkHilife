package com.freebuds.controller.device

enum class DeviceProfile(val modelName: String, val supportedFeatures: Set<Feature>) {
    GENERIC("Generic Huawei", setOf(
        Feature.BATTERY, Feature.ANC, Feature.GESTURES
    )),
    FREE_BUDS_4I("FreeBuds 4i", setOf(
        Feature.BATTERY, Feature.ANC, Feature.GESTURES,
        Feature.AUTO_PAUSE, Feature.EQUALIZER, Feature.ANC_LEVEL,
        Feature.VOICE_LANGUAGE
    )),
    FREE_BUDS_5I("FreeBuds 5i", setOf(
        Feature.BATTERY, Feature.ANC, Feature.GESTURES,
        Feature.AUTO_PAUSE, Feature.EQUALIZER, Feature.LOW_LATENCY,
        Feature.SOUND_QUALITY, Feature.ANC_LEVEL, Feature.VOICE_LANGUAGE
    )),
    FREE_BUDS_6I("FreeBuds 6i", setOf(
        Feature.BATTERY, Feature.ANC, Feature.GESTURES,
        Feature.AUTO_PAUSE, Feature.EQUALIZER, Feature.LOW_LATENCY,
        Feature.SOUND_QUALITY, Feature.ANC_LEVEL, Feature.VOICE_LANGUAGE
    )),
    FREE_BUDS_PRO("FreeBuds Pro", setOf(
        Feature.BATTERY, Feature.ANC, Feature.GESTURES,
        Feature.EQUALIZER, Feature.DUAL_CONNECT, Feature.ANC_LEVEL,
        Feature.VOICE_LANGUAGE
    )),
    FREE_BUDS_PRO_2("FreeBuds Pro 2", setOf(
        Feature.BATTERY, Feature.ANC, Feature.GESTURES,
        Feature.EQUALIZER, Feature.SOUND_QUALITY, Feature.DUAL_CONNECT,
        Feature.ANC_LEVEL, Feature.ANC_DYNAMIC, Feature.VOICE_LANGUAGE
    )),
    FREE_BUDS_PRO_3("FreeBuds Pro 3", setOf(
        Feature.BATTERY, Feature.ANC, Feature.GESTURES,
        Feature.EQUALIZER, Feature.LOW_LATENCY, Feature.SOUND_QUALITY,
        Feature.DUAL_CONNECT, Feature.ANC_LEVEL, Feature.ANC_DYNAMIC,
        Feature.VOICE_LANGUAGE
    )),
    FREE_LACE_PRO_2("FreeLace Pro 2", setOf(
        Feature.BATTERY, Feature.ANC, Feature.GESTURES,
        Feature.EQUALIZER, Feature.LOW_LATENCY, Feature.SOUND_QUALITY,
        Feature.DUAL_CONNECT, Feature.ANC_LEVEL
    ))
}

enum class Feature {
    BATTERY, ANC, GESTURES, AUTO_PAUSE, EQUALIZER,
    LOW_LATENCY, SOUND_QUALITY, DUAL_CONNECT,
    ANC_LEVEL, ANC_DYNAMIC, VOICE_LANGUAGE
}
