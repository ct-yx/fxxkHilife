package com.freebuds.controller.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.freebuds.controller.data.ConnectionCommand
import com.freebuds.controller.data.ConnectionTrigger
import com.freebuds.controller.data.EarbudConnectionManager
import com.freebuds.controller.util.LogBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Observes Android's system Bluetooth link and turns profile/broadcast events into typed
 * connection commands.  The foreground service owns notification/UI work; it no longer needs to
 * know how ACL, A2DP, HEADSET and periodic profile checks are wired together.
 */
class SystemBluetoothMonitor(
    private val context: Context,
    private val connectionManager: EarbudConnectionManager,
    private val savedAddresses: () -> Set<String>,
    private val scope: CoroutineScope,
) {
    private var receiver: BroadcastReceiver? = null
    private var monitorJob: Job? = null
    private var a2dpProfile: BluetoothProfile? = null
    private var headsetProfile: BluetoothProfile? = null

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            when (profile) {
                BluetoothProfile.A2DP -> a2dpProfile = proxy
                BluetoothProfile.HEADSET -> headsetProfile = proxy
            }
            triggerFromKnownProfiles(
                reason = "Bluetooth profile connected",
                trigger = ConnectionTrigger.AudioProfileConnected,
            )
        }

        override fun onServiceDisconnected(profile: Int) {
            when (profile) {
                BluetoothProfile.A2DP -> a2dpProfile = null
                BluetoothProfile.HEADSET -> headsetProfile = null
            }
        }
    }

    fun start() {
        if (receiver != null) return
        registerReceiver()
        registerProfiles()
        monitorJob?.cancel()
        monitorJob = scope.launch {
            delay(INITIAL_CHECK_DELAY_MS)
            while (isActive) {
                triggerFromKnownProfiles(
                    reason = "periodic system-connected check",
                    logMisses = false,
                    trigger = ConnectionTrigger.PeriodicCheck,
                )
                delay(PERIODIC_CHECK_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        receiver?.let { registered -> runCatching { context.unregisterReceiver(registered) } }
        receiver = null
        val adapter = BluetoothAdapter.getDefaultAdapter()
        a2dpProfile?.let { proxy ->
            runCatching { adapter?.closeProfileProxy(BluetoothProfile.A2DP, proxy) }
        }
        headsetProfile?.let { proxy ->
            runCatching { adapter?.closeProfileProxy(BluetoothProfile.HEADSET, proxy) }
        }
        a2dpProfile = null
        headsetProfile = null
    }

    private fun registerReceiver() {
        val systemReceiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                val action = intent?.action ?: return
                val device = runCatching {
                    intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                }.getOrNull()
                val address = device?.address
                val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                when {
                    action == BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        LogBuffer.i("AutoConnect", "System ACL connected address=${address ?: "unknown"}")
                        triggerAutoControlConnect(
                            reason = "ACL connected",
                            knownDevice = device,
                            force = true,
                            trigger = ConnectionTrigger.AclConnected,
                        )
                    }
                    action == BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED &&
                        state == BluetoothProfile.STATE_CONNECTED -> {
                        LogBuffer.i("AutoConnect", "System A2DP connected address=${address ?: "unknown"}")
                        triggerAutoControlConnect(
                            reason = "A2DP connected",
                            knownDevice = device,
                            force = true,
                            trigger = ConnectionTrigger.AudioProfileConnected,
                        )
                    }
                    action == BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED &&
                        state == BluetoothProfile.STATE_CONNECTED -> {
                        LogBuffer.i("AutoConnect", "System HEADSET connected address=${address ?: "unknown"}")
                        triggerAutoControlConnect(
                            reason = "HEADSET connected",
                            knownDevice = device,
                            force = true,
                            trigger = ConnectionTrigger.AudioProfileConnected,
                        )
                    }
                    action == BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        LogBuffer.i("AutoConnect", "System ACL disconnected address=${address ?: "unknown"}")
                        address?.let {
                            connectionManager.submit(ConnectionCommand.SystemAclDisconnected(it))
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Bluetooth profile broadcasts originate from system components. NOT_EXPORTED can
            // silently miss them on Android 13+ ROMs.
            context.registerReceiver(systemReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(systemReceiver, filter)
        }
        receiver = systemReceiver
        LogBuffer.i("AutoConnect", "Background system Bluetooth monitor registered")
    }

    @SuppressLint("MissingPermission")
    private fun registerProfiles() {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        adapter.getProfileProxy(context, profileListener, BluetoothProfile.A2DP)
        adapter.getProfileProxy(context, profileListener, BluetoothProfile.HEADSET)
    }

    @SuppressLint("MissingPermission")
    private fun knownProfileDevices(): List<BluetoothDevice> = buildList {
        addAll(runCatching { a2dpProfile?.connectedDevices.orEmpty() }.getOrDefault(emptyList()))
        addAll(runCatching { headsetProfile?.connectedDevices.orEmpty() }.getOrDefault(emptyList()))
    }.distinctBy { it.address }

    private fun triggerFromKnownProfiles(
        reason: String,
        logMisses: Boolean = true,
        trigger: ConnectionTrigger = ConnectionTrigger.AudioProfileConnected,
    ) {
        val saved = savedAddresses()
        val device = knownProfileDevices().firstOrNull { it.address in saved }
        triggerAutoControlConnect(
            reason = reason,
            knownDevice = device,
            force = device != null,
            logMisses = logMisses,
            trigger = trigger,
        )
    }

    private fun triggerAutoControlConnect(
        reason: String,
        knownDevice: BluetoothDevice? = null,
        force: Boolean = false,
        logMisses: Boolean = true,
        trigger: ConnectionTrigger = ConnectionTrigger.ServiceCommand,
    ) {
        connectionManager.submit(
            ConnectionCommand.TriggerAutoConnect(
                reason = reason,
                knownDevice = knownDevice,
                force = force,
                logMisses = logMisses,
                trigger = trigger,
            )
        )
    }

    companion object {
        private const val INITIAL_CHECK_DELAY_MS = 1_000L
        private const val PERIODIC_CHECK_INTERVAL_MS = 30_000L
    }
}
