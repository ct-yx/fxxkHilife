package com.freebuds.controller.bluetooth

/**
 * Interface for SPP client — enables testing/mocking.
 * Signatures must match [SppClient] exactly.
 */
interface ITestSppClient {
    suspend fun connect(timeoutMs: Long = 10000): Boolean
    suspend fun send(pkg: SppPackage, timeoutMs: Long = 5000, maxRetries: Int = 3, retryDelayMs: Long = 200): SppPackage?
    fun registerHandler(cmdId: ByteArray, handler: suspend (SppPackage) -> Unit)
    fun disconnect(reason: String = "User requested")
    fun destroy()
}
