package com.freebuds.controller.adapter.huawei.protocol

import com.freebuds.controller.bluetooth.HuaweiDeviceHandler
import com.freebuds.controller.protocol.HuaweiCapability
import org.junit.Assert.assertEquals
import org.junit.Test

class HuaweiHandlerRegistryTest {
    @Test
    fun duplicateHandlerIdsAreRegisteredOnlyOnce() {
        val registry = HuaweiHandlerRegistry()

        registry.register(FakeHandler("battery"))
        registry.register(FakeHandler("battery"))

        assertEquals(1, registry.allHandlers().size)
        assertEquals(1, registry.handlersForCommand("01").size)
    }

    private class FakeHandler(override val id: String) : HuaweiDeviceHandler {
        override val commandIds: List<ByteArray> = listOf(byteArrayOf(0x01))
        override val capabilities: List<HuaweiCapability> = emptyList()
    }
}
