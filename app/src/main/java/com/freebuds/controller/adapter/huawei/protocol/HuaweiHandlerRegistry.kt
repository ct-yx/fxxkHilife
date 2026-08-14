package com.freebuds.controller.adapter.huawei.protocol

import com.freebuds.controller.bluetooth.HuaweiDeviceHandler
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Registry for Huawei/OpenFreebuds handlers.
 *
 * Keeps command routing, ignored commands, property routing and failed init bookkeeping out of
 * SppDriver. SppDriver still owns the legacy connection/session flow for now, but no longer needs
 * to know how handler maps are built.
 */
class HuaweiHandlerRegistry {
    private val handlers = mutableListOf<HuaweiDeviceHandler>()
    private val commandHandlers = mutableMapOf<String, MutableList<HuaweiDeviceHandler>>()
    private val ignoredCommands = mutableSetOf<String>()
    private val propertyHandlers = mutableMapOf<String, HuaweiDeviceHandler>()
    val failedHandlerIds: MutableSet<String> = CopyOnWriteArraySet()

    fun register(handler: HuaweiDeviceHandler) {
        // Handler ids are the initialization and property-routing identity. Registering a new
        // instance with the same id would otherwise duplicate reads, notifications and writes.
        if (handlers.any { it.id == handler.id }) return
        handlers.add(handler)
        for (cmd in handler.commandIds) {
            val key = cmd.toHexKey()
            commandHandlers.getOrPut(key) { mutableListOf() }.apply {
                if (handler !in this) add(handler)
            }
        }
        for (cmd in handler.ignoreCommandIds) {
            ignoredCommands.add(cmd.toHexKey())
        }
        for ((group, prop) in handler.properties) {
            propertyHandlers[propertyKey(group, prop)] = handler
        }
    }

    fun allHandlers(): List<HuaweiDeviceHandler> = handlers.toList()

    fun findById(id: String): HuaweiDeviceHandler? = handlers.find { it.id == id }

    fun hasCommand(commandKey: String): Boolean =
        commandHandlers.containsKey(commandKey) || commandKey in ignoredCommands

    fun handlerForCommand(commandKey: String): HuaweiDeviceHandler? =
        commandHandlers[commandKey]?.firstOrNull()

    /** Multiple features may legitimately observe the same notification command. */
    fun handlersForCommand(commandKey: String): List<HuaweiDeviceHandler> =
        commandHandlers[commandKey]?.toList().orEmpty()

    fun handlerForProperty(group: String, prop: String): HuaweiDeviceHandler? =
        propertyHandlers[propertyKey(group, prop)] ?: propertyHandlers[propertyKey(group, "")]

    private fun propertyKey(group: String, prop: String): String = "$group//$prop"
}

private fun ByteArray.toHexKey(): String = joinToString("") { "%02x".format(it) }
