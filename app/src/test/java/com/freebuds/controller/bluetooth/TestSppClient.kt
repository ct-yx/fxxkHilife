package com.freebuds.controller.bluetooth

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A minimal in-memory ISppClient implementation for Handler unit tests.
 *
 * Captures sent packages for assertion, and can be pre-configured
 * with canned responses for specific command IDs.
 */
class TestSppClient : ISppClient {

    /** Configured canned responses: commandId hex string -> response SppPackage */
    private val responses = mutableMapOf<String, SppPackage>()

    /** All packages that were sent through this client (for assertion) */
    val sentPackages = mutableListOf<SppPackage>()

    /** Registered handler callbacks: commandId hex string -> callback */
    private val handlers = mutableMapOf<String, suspend (SppPackage) -> Unit>()

    override suspend fun send(pkg: SppPackage, timeoutMs: Long): SppPackage? {
        sentPackages.add(pkg)
        val key = pkg.commandId.joinToString("") { "%02x".format(it) }
        return responses[key]
    }

    override fun registerHandler(cmdId: ByteArray, handler: suspend (SppPackage) -> Unit) {
        val key = cmdId.joinToString("") { "%02x".format(it) }
        handlers[key] = handler
    }

    /** Set a canned response for a given command ID (e.g. "0109" for ANC_MODE_READ). */
    fun enqueueResponse(commandId: ByteArray, response: SppPackage) {
        val key = commandId.joinToString("") { "%02x".format(it) }
        responses[key] = response
    }

    /** Check whether a handler was registered for the given command ID. */
    fun hasHandlerFor(cmdId: ByteArray): Boolean {
        val key = cmdId.joinToString("") { "%02x".format(it) }
        return key in handlers
    }

    /** Invoke the registered handler for the given command ID (to simulate incoming notification). */
    suspend fun triggerHandler(cmdId: ByteArray, pkg: SppPackage) {
        val key = cmdId.joinToString("") { "%02x".format(it) }
        handlers[key]?.invoke(pkg)
    }

    /** Reset all captures and responses. */
    fun reset() {
        sentPackages.clear()
        handlers.clear()
        responses.clear()
    }
}
