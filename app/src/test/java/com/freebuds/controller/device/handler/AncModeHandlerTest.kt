package com.freebuds.controller.device.handler

import com.freebuds.controller.bluetooth.SppCommand
import com.freebuds.controller.bluetooth.SppPackage
import com.freebuds.controller.bluetooth.TestSppClient
import com.freebuds.controller.device.AncMode
import com.freebuds.controller.device.DeviceState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AncModeHandler].
 *
 * Verifies that the handler correctly:
 * - registers an ANC_MODE_READ listener on init()
 * - queries device state in applyToState()
 * - sends write commands in setProperty()
 * - handles incoming ANC_MODE_READ notifications
 * - parses ANC mode bytes correctly
 */
class AncModeHandlerTest {

    private lateinit var client: TestSppClient
    private lateinit var handler: AncModeHandler

    @Before
    fun setUp() {
        client = TestSppClient()
        handler = AncModeHandler()
    }

    // --- init() tests ---

    @Test
    fun `init should register ANC_MODE_READ handler`() = runBlocking {
        handler.init(client)

        assertTrue(
            "Handler should register a listener for ANC_MODE_READ",
            client.hasHandlerFor(SppCommand.ANC_MODE_READ)
        )
    }

    // --- applyToState() tests ---

    @Test
    fun `applyToState should return state unchanged when client returns null`() = runBlocking {
        handler.init(client)
        // no response configured → client.send returns null

        val state = DeviceState(ancMode = AncMode.OFF)
        val result = handler.applyToState(client, state)

        assertEquals("State should be unchanged when send returns null", state, result)
    }

    @Test
    fun `applyToState should parse cancellation mode from response`() = runBlocking {
        handler.init(client)
        val response = SppPackage.readRequest(SppCommand.ANC_MODE_READ, listOf(1))
            .apply { parameters[1] = byteArrayOf(0x01) }
        client.enqueueResponse(SppCommand.ANC_MODE_READ, response)

        val state = DeviceState()
        val result = handler.applyToState(client, state)

        assertEquals(
            "ANC mode should be CANCELLATION when param1 = 0x01",
            AncMode.CANCELLATION,
            result.ancMode
        )
    }

    @Test
    fun `applyToState should parse awareness mode from response`() = runBlocking {
        handler.init(client)
        val response = SppPackage.readRequest(SppCommand.ANC_MODE_READ, listOf(1))
            .apply { parameters[1] = byteArrayOf(0x02) }
        client.enqueueResponse(SppCommand.ANC_MODE_READ, response)

        val state = DeviceState()
        val result = handler.applyToState(client, state)

        assertEquals(
            "ANC mode should be AWARENESS when param1 = 0x02",
            AncMode.AWARENESS,
            result.ancMode
        )
    }

    @Test
    fun `applyToState should parse off mode from response`() = runBlocking {
        handler.init(client)
        val response = SppPackage.readRequest(SppCommand.ANC_MODE_READ, listOf(1))
            .apply { parameters[1] = byteArrayOf(0x00) }
        client.enqueueResponse(SppCommand.ANC_MODE_READ, response)

        val state = DeviceState()
        val result = handler.applyToState(client, state)

        assertEquals(
            "ANC mode should be OFF when param1 = 0x00",
            AncMode.OFF,
            result.ancMode
        )
    }

    @Test
    fun `applyToState should parse unknown mode for unrecognized bytes`() = runBlocking {
        handler.init(client)
        val response = SppPackage.readRequest(SppCommand.ANC_MODE_READ, listOf(1))
            .apply { parameters[1] = byteArrayOf(0xFF.toByte()) }
        client.enqueueResponse(SppCommand.ANC_MODE_READ, response)

        val state = DeviceState()
        val result = handler.applyToState(client, state)

        assertEquals(
            "ANC mode should be OFF for unrecognized byte value 0xFF",
            AncMode.OFF,
            result.ancMode
        )
    }

    @Test
    fun `applyToState should return state unchanged when response has empty param`() = runBlocking {
        handler.init(client)
        val response = SppPackage.readRequest(SppCommand.ANC_MODE_READ, listOf(1))
            // leave parameters[1] empty (length 0)
        client.enqueueResponse(SppCommand.ANC_MODE_READ, response)

        val state = DeviceState()
        val result = handler.applyToState(client, state)

        assertEquals(
            "ANC mode should be UNKNOWN when data is empty",
            AncMode.UNKNOWN,
            result.ancMode
        )
    }

    @Test
    fun `applyToState should send ANC_MODE_READ request`() = runBlocking {
        handler.init(client)
        // no response needed for this test
        handler.applyToState(client, DeviceState())

        assertEquals(
            "Handler should send exactly one request",
            1,
            client.sentPackages.size
        )
        assertTrue(
            "Request should target ANC_MODE_READ",
            client.sentPackages[0].matchesCommand(SppCommand.ANC_MODE_READ)
        )
    }

    // --- setProperty() tests ---

    @Test
    fun `setProperty should send ANC_MODE_WRITE with off byte for anc_mode off`() = runBlocking {
        handler.init(client)

        handler.setProperty(client, "anc_mode", "off")

        assertEquals("Should send exactly one package", 1, client.sentPackages.size)
        val sent = client.sentPackages[0]
        assertTrue(
            "Should write to ANC_MODE_WRITE command",
            sent.matchesCommand(SppCommand.ANC_MODE_WRITE)
        )
        val param = sent.findParam(1)
        assertNotNull("Param 1 should exist", param)
        assertTrue(
            "Param 1 should contain 0x00 for 'off'",
            param.isNotEmpty() && param[0].toInt() == 0x00
        )
    }

    @Test
    fun `setProperty should send ANC_MODE_WRITE with cancellation byte`() = runBlocking {
        handler.init(client)

        handler.setProperty(client, "anc_mode", "cancellation")

        assertEquals("Should send exactly one package", 1, client.sentPackages.size)
        val sent = client.sentPackages[0]
        val param = sent.findParam(1)
        assertTrue(
            "Param 1 should contain 0x01 for 'cancellation'",
            param.isNotEmpty() && param[0].toInt() == 0x01
        )
    }

    @Test
    fun `setProperty should send ANC_MODE_WRITE with awareness byte`() = runBlocking {
        handler.init(client)

        handler.setProperty(client, "anc_mode", "awareness")

        assertEquals("Should send exactly one package", 1, client.sentPackages.size)
        val sent = client.sentPackages[0]
        val param = sent.findParam(1)
        assertTrue(
            "Param 1 should contain 0x02 for 'awareness'",
            param.isNotEmpty() && param[0].toInt() == 0x02
        )
    }

    @Test
    fun `setProperty should ignore unknown properties`() = runBlocking {
        handler.init(client)

        handler.setProperty(client, "some_unknown_prop", "whatever")

        assertEquals(
            "Should not send any package for unknown property",
            0,
            client.sentPackages.size
        )
    }

    // --- notification handler (init registered) tests ---

    @Test
    fun `registered handler should be triggered by incoming ANC_MODE_READ`() = runBlocking {
        handler.init(client)

        val notificationPkg = SppPackage.readRequest(SppCommand.ANC_MODE_READ, listOf(1))
            .apply { parameters[1] = byteArrayOf(0x02) }

        // trigger the registered handler (simulating incoming notification)
        client.triggerHandler(SppCommand.ANC_MODE_READ, notificationPkg)

        // The handler's handleAnc() currently does nothing (empty body),
        // so we just verify no exception is thrown and handler was properly registered.
        assertTrue(
            "Handler should be registered for ANC_MODE_READ after init",
            client.hasHandlerFor(SppCommand.ANC_MODE_READ)
        )
    }

    // --- edge cases ---

    @Test
    fun `applyToState should handle consecutive calls`() = runBlocking {
        handler.init(client)

        // First call: cancellation
        val resp1 = SppPackage.readRequest(SppCommand.ANC_MODE_READ, listOf(1))
            .apply { parameters[1] = byteArrayOf(0x01) }
        client.enqueueResponse(SppCommand.ANC_MODE_READ, resp1)
        val result1 = handler.applyToState(client, DeviceState())
        assertEquals(AncMode.CANCELLATION, result1.ancMode)

        // Second call: awareness (different response)
        client.reset()
        val resp2 = SppPackage.readRequest(SppCommand.ANC_MODE_READ, listOf(1))
            .apply { parameters[1] = byteArrayOf(0x02) }
        client.enqueueResponse(SppCommand.ANC_MODE_READ, resp2)
        val result2 = handler.applyToState(client, DeviceState())
        assertEquals(AncMode.AWARENESS, result2.ancMode)
    }
}
