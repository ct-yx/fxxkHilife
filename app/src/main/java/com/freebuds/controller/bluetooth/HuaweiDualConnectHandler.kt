package com.freebuds.controller.bluetooth

import com.freebuds.controller.adapter.huawei.protocol.HuaweiCommandCatalog
import com.freebuds.controller.protocol.HuaweiCapability
import com.freebuds.controller.protocol.HuaweiSppPackage
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

class DualConnectHandler(
    private val wAutoConnect: Boolean = true,
) : HuaweiDeviceHandler {
    private val enabledCommand = HuaweiCommandCatalog.dualConnectEnabled
    private val enumerationCommand = HuaweiCommandCatalog.dualConnectEnumeration
    private val preferredCommand = HuaweiCommandCatalog.dualConnectPreferred
    private val executeCommand = HuaweiCommandCatalog.dualConnectExecute
    override val id = "dual_connect"
    override val initTimeoutMs = 1_200L
    override val initAttemptMax = 6
    override val commandIds = listOf(
        *enumerationCommand.incomingCommandIds.toTypedArray(),
        *enabledCommand.incomingCommandIds.toTypedArray(),
    )
    override val ignoreCommandIds = listOf(
        enabledCommand.writeCommand!!,
        preferredCommand.writeCommand!!,
        executeCommand.writeCommand!!,
    )
    override val properties = listOf("dual_connect" to "")
    override val capabilities = listOf(HuaweiCapability.DUAL_CONNECT, HuaweiCapability.DUAL_CONNECT_AUTO)

    private val pendingDevices = mutableMapOf<Int, DualConnectRow>()
    private var expectedCount = Int.MAX_VALUE

    override suspend fun onInit(driver: SppDriver) {
        pendingDevices.clear()
        expectedCount = Int.MAX_VALUE
        requestEnabledNoWait(driver)
        driver.sendNowait(
            enumerationCommand.writeRequestNoWait(1 to byteArrayOf()),
            operation = "dual_connect.enumerate",
        )
        waitForEnumeration(driver)
    }

    override suspend fun onDriverPackage(driver: SppDriver, pkg: HuaweiSppPackage) {
        if (pkg.commandId.contentEquals(enumerationCommand.notificationCommands.first())) {
            com.freebuds.controller.util.LogBuffer.i("SPP", "dual_connect change event received; scheduling refresh")
            onInit(driver)
            return
        }
        onPackage(pkg, driver)
    }

    private suspend fun readEnabled(driver: SppDriver) {
        driver.sendPackage(enabledCommand.readRequest(), timeout = 1000, operation = "dual_connect.enabled.read")
            ?.let { pkg ->
                val enabled = pkg.findParam(1).firstOrNull()?.toInt() == 1
                driver.putProperty("dual_connect", "enabled", enabled.asString())
            }
    }

    private suspend fun requestEnabledNoWait(driver: SppDriver) {
        driver.sendNowait(
            enabledCommand.readRequestNoWait(),
            operation = "dual_connect.enabled.notify",
        )
    }

    private suspend fun onPackage(pkg: HuaweiSppPackage, driver: SppDriver) {
        when {
            pkg.commandId.contentEquals(enabledCommand.readCommand) -> {
                val enabled = pkg.findParam(1).firstOrNull()?.toInt() == 1
                driver.putProperty("dual_connect", "enabled", enabled.asString())
            }
            pkg.commandId.contentEquals(enumerationCommand.readCommand) -> {
                val row = parseRow(pkg) ?: return
                expectedCount = pkg.findParam(2).toPositiveIntOrNull() ?: expectedCount
                val index = pkg.findParam(3).toPositiveIntOrNull() ?: pendingDevices.size
                pendingDevices[index] = row
                com.freebuds.controller.util.LogBuffer.i(
                    "SPP",
                    "dual_connect row received index=$index expected=$expectedCount address=${row.address} connected=${row.connected}"
                )
                publishRows(driver)
            }
        }
    }

    private suspend fun waitForEnumeration(driver: SppDriver) {
        val completed = withTimeoutOrNull(1_000) {
            while (pendingDevices.isEmpty() || pendingDevices.size < expectedCount) {
                delay(80)
            }
            true
        } == true
        if (!completed && pendingDevices.isNotEmpty()) {
            com.freebuds.controller.util.LogBuffer.i(
                "SPP",
                "dual_connect accepted partial enumeration rows=${pendingDevices.size} expected=$expectedCount"
            )
        }
        publishRows(driver)
    }

    private suspend fun publishRows(driver: SppDriver) {
        val ordered = pendingDevices.toSortedMap().values.toList()
        val rows = ordered.joinToString("|") { row ->
            listOf(
                row.address,
                row.name.encodeForList(),
                row.autoConnect?.asString() ?: "",
                row.preferred.asString(),
                row.connected.asString(),
                row.playing.asString(),
            ).joinToString(";")
        }
        val preferred = ordered.firstOrNull { it.preferred }?.address.orEmpty()
        driver.putProperty("dual_connect", "devices", rows)
        driver.putProperty("dual_connect", "preferred_device", preferred)
        if (rows.isNotEmpty()) {
            com.freebuds.controller.util.LogBuffer.d("SPP", "dual_connect published ${ordered.size} devices")
        }
    }

    private fun parseRow(pkg: HuaweiSppPackage): DualConnectRow? {
        val address = pkg.findParam(4).joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        if (address.length < 12) return null
        val connState = pkg.findParam(5).firstOrNull()?.toInt() ?: 0
        val preferred = pkg.findParam(7).firstOrNull()?.toInt() == 1
        val autoConnect = if (wAutoConnect) pkg.findParam(8).firstOrNull()?.let { it.toInt() == 1 } else null
        val name = String(pkg.findParam(9), Charsets.UTF_8).ifBlank { address }
        return DualConnectRow(
            address = address,
            name = name,
            autoConnect = autoConnect,
            preferred = preferred,
            connected = connState > 0,
            playing = connState == 9,
        )
    }

    override suspend fun setProperty(driver: SppDriver, group: String, prop: String, value: String) {
        when {
            prop == "enabled" -> {
                driver.putProperty(group, prop, value)
                driver.sendPackage(
                    enabledCommand.writeRequest(1 to b(if (value == "true") 1 else 0)),
                    timeout = 1000,
                    operation = "dual_connect.enabled.write",
                )
                readEnabled(driver)
            }
            prop == "preferred_device" -> {
                val address = value.normalizeMacHex() ?: return
                driver.putProperty(group, prop, address)
                driver.sendNowait(
                    preferredCommand.writeRequestNoWait(1 to address.hexToBytes()),
                    operation = "dual_connect.preferred.write",
                )
                onInit(driver)
            }
            prop.endsWith(":auto_connect") -> {
                val address = prop.substringBefore(":").normalizeMacHex() ?: return
                val cmd = if (value == "true") 4 else 5
                driver.sendNowait(
                    executeCommand.writeRequestNoWait(cmd to address.hexToBytes()),
                    operation = "dual_connect.auto_connect.write",
                )
                onInit(driver)
            }
            prop.endsWith(":connected") -> {
                val address = prop.substringBefore(":").normalizeMacHex() ?: return
                val cmd = if (value == "true") 1 else 2
                driver.sendNowait(
                    executeCommand.writeRequestNoWait(cmd to address.hexToBytes()),
                    operation = "dual_connect.connected.write",
                )
                onInit(driver)
            }
            prop.endsWith(":name") && value.isBlank() -> {
                val address = prop.substringBefore(":").normalizeMacHex() ?: return
                driver.sendNowait(
                    executeCommand.writeRequestNoWait(3 to address.hexToBytes()),
                    operation = "dual_connect.name_refresh.write",
                )
                onInit(driver)
            }
            prop == "refresh" -> onInit(driver)
            else -> com.freebuds.controller.util.LogBuffer.w("SPP", "Unknown dual_connect.$prop=$value")
        }
    }

    private data class DualConnectRow(
        val address: String,
        val name: String,
        val autoConnect: Boolean?,
        val preferred: Boolean,
        val connected: Boolean,
        val playing: Boolean,
    )

    private fun ByteArray.toPositiveIntOrNull(): Int? {
        if (isEmpty()) return null
        return fold(0) { acc, byte -> (acc shl 8) or (byte.toInt() and 0xFF) }
    }

    private fun String.encodeForList(): String = replace("%", "%25").replace(";", "%3B").replace("|", "%7C")

    private fun String.normalizeMacHex(): String? {
        val normalized = filter { it.isDigit() || it.lowercaseChar() in 'a'..'f' }.lowercase()
        return normalized.takeIf { it.length == 12 }
    }

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
