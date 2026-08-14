package com.freebuds.controller.data

import android.bluetooth.BluetoothDevice

/** Narrow repository-facing host contract used by [EarbudConnectionManager]. */
internal interface ConnectionManagerHost {
    fun connect(device: BluetoothDevice, trigger: ConnectionTrigger = ConnectionTrigger.UserAction): String?
    fun autoConnectSavedRequest(address: String, logMisses: Boolean, trigger: ConnectionTrigger): ConnectionRequestResult
    fun autoConnectKnownSystemConnectedRequest(device: BluetoothDevice, logMisses: Boolean, trigger: ConnectionTrigger): ConnectionRequestResult
    fun autoConnectLastSavedRequest(trigger: ConnectionTrigger): ConnectionRequestResult
    fun autoConnectSystemConnectedSavedRequest(logMisses: Boolean, trigger: ConnectionTrigger): ConnectionRequestResult
    fun clearManualDisconnectSuppression()
    fun handleSystemBluetoothDisconnected(address: String)
    fun disconnect()
    fun refreshSavedDeviceConnections()
    fun regressionSimulateAclDisconnect()
    fun getActiveConnectionAttemptId(address: String? = null): String?
    fun setHardwareRegressionActive(active: Boolean)
}
