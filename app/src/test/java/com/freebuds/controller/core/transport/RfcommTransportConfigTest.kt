package com.freebuds.controller.core.transport

import com.freebuds.controller.adapter.huawei.HuaweiOpenFreebudsAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RfcommTransportConfigTest {
    @Test
    fun compatibilityFallbackUsesChannelOneAndRecordsSource() {
        val config = RfcommTransportConfig.compatibilityFallback()

        assertEquals(1, config.channel)
        assertEquals(EndpointSource.CompatibilityFallback, config.source)
        assertTrue(config.endpointDescription().contains("rfcomm-channel=1"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidChannel() {
        RfcommTransportConfig(channel = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidConnectTimeout() {
        RfcommTransportConfig(connectTimeoutMs = 0)
    }

    @Test
    fun adapterSelectsModelChannelAndFallbackSource() {
        val known = HuaweiOpenFreebudsAdapter.rfcommTransportConfig("HUAWEI FreeBuds 6i")
        val unknown = HuaweiOpenFreebudsAdapter.rfcommTransportConfig("HUAWEI Unknown Buds")

        assertEquals(1, known.channel)
        assertEquals(EndpointSource.VerifiedModelConfig, known.source)
        assertEquals(1, unknown.channel)
        assertEquals(EndpointSource.CompatibilityFallback, unknown.source)
    }
}
