package com.freebuds.controller.bluetooth

/**
 * Interface extracted from SppClient for testability.
 *
 * Allows Handlers to accept any implementation (real SppClient or test mock)
 * without type mismatch at compile time.
 */
interface ISppClient {
    /**
     * Send a package and wait for its response.
     * Returns null if the command does not expect a response or on timeout.
     */
    suspend fun send(pkg: SppPackage, timeoutMs: Long = 5000): SppPackage?

    /**
     * Register an incoming-package handler for the given command ID.
     * The callback will be invoked when an unsolicited package with that ID arrives.
     */
    fun registerHandler(cmdId: ByteArray, handler: suspend (SppPackage) -> Unit)
}
