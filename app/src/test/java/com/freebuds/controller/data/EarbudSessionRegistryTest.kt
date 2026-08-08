package com.freebuds.controller.data

import com.freebuds.controller.core.session.EarbudSession
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EarbudSessionRegistryTest {
    @Test
    fun replacementCannotBeInstalledUntilThePreviousSessionIsDetached() {
        val registry = EarbudSessionRegistry()
        val first = FakeSession()
        val second = FakeSession()

        assertTrue(registry.install("attempt-1", "AA", first))
        assertFalse(registry.install("attempt-2", "AA", second))

        val detached = registry.detach()
        assertNotNull(detached)
        assertSame(first, detached!!.session)
        assertTrue(registry.install("attempt-2", "AA", second))
    }

    @Test
    fun staleSessionCannotDetachTheCurrentSession() {
        val registry = EarbudSessionRegistry()
        val first = FakeSession()
        val second = FakeSession()

        registry.install("attempt-1", "AA", first)
        registry.detach()
        registry.install("attempt-2", "AA", second)

        assertTrue(registry.detachIfCurrent(first) == null)
        assertSame(second, registry.currentSession())
    }

    private class FakeSession : EarbudSession {
        override val id = "fake"
        override val displayName = "fake"
        override val isConnected = false
        override val failedHandlerIds: Set<String> = emptySet()
        override val handlerIds: List<String> = emptyList()
        override fun setPropertyChangedListener(listener: (() -> Unit)?) = Unit
        override fun setDisconnectedListener(listener: (() -> Unit)?) = Unit
        override fun registerHandler(handler: com.freebuds.controller.bluetooth.HuaweiDeviceHandler) = Unit
        override fun getHandlerById(id: String) = null
        override suspend fun connect() = false
        override fun disconnect() = Unit
        override suspend fun initializeCoreHandlers(timeoutMs: Long, maxAttempts: Int) = Unit
        override suspend fun initializeDeferredHandlers() = Unit
        override suspend fun setProperty(group: String, prop: String, value: String) = Unit
        override suspend fun mapState(
            failedHandlers: Collection<String>,
            connectedSince: Long?,
        ) = DeviceProps()
        override fun legacyDriverOrNull() = null
    }
}
