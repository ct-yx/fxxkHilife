package com.freebuds.controller.bluetooth

import com.freebuds.controller.adapter.huawei.protocol.HuaweiCommandCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AncStatePolicyTest {
    @Test
    fun onlyTheReadCommandIsAnAuthoritativeStateSnapshot() {
        assertEquals(
            AncPacketAction.APPLY_STATE,
            ancPacketAction(HuaweiCommandCatalog.anc.readCommand),
        )
        assertEquals(
            AncPacketAction.REFRESH_STATE,
            ancPacketAction(HuaweiCommandCatalog.anc.notificationCommands.single()),
        )
        assertEquals(
            AncPacketAction.IGNORE,
            ancPacketAction(byteArrayOf(0x2b, 0x5e)),
        )
    }

    @Test
    fun oneByteAndTwoByteAncPayloadsDecodeToModeAndLevel() {
        assertEquals(AncReading(mode = 2, level = 0), decodeAncReading(byteArrayOf(2)))
        assertEquals(AncReading(mode = 1, level = 3), decodeAncReading(byteArrayOf(3, 1)))
        assertNull(decodeAncReading(byteArrayOf()))
    }
}
