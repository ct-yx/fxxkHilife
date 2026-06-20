package com.freebuds.controller.ui.screen

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.freebuds.controller.R
import com.freebuds.controller.FreeBudsApp
import com.freebuds.controller.bluetooth.ConnectionState
import com.freebuds.controller.device.DeviceManager
import com.freebuds.controller.device.DeviceProfile
import com.freebuds.controller.device.DeviceState
import com.freebuds.controller.ui.component.BatteryCard
import com.freebuds.controller.util.PermissionHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    deviceManager: DeviceManager,
    onNavigateToSettings: () -> Unit
) {
    val state by deviceManager.state.collectAsState()
    val connState by deviceManager.connectionState.collectAsState()
    val scope = rememberCoroutineScope()
    var showDevicePicker by remember { mutableStateOf(false) }
    val isConnecting = connState == ConnectionState.CONNECTING
    var btnLock by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold) },
                actions = {
                    // Language toggle
                    val lang by FreeBudsApp.instance.preferences.language.collectAsState(initial = "en")
                    IconButton(onClick = {
                        if (!btnLock) {
                            btnLock = true
                            scope.launch {
                                val newLang = if (lang == "en") "zh" else "en"
                                FreeBudsApp.instance.preferences.setLanguage(newLang)
                                FreeBudsApp.instance.updateLocale(newLang)
                                // Force activity recreate to re-inflate all string resources
                                (context as? android.app.Activity)?.recreate()
                                delay(500); btnLock = false
                            }
                        }
                    }) {
                        Text(
                            text = if (lang == "en") "中" else "EN",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        if (!btnLock) {
                            btnLock = true
                            onNavigateToSettings()
                            scope.launch { delay(500); btnLock = false }
                        }
                    }) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.screen_settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                ConnectionCard(
                    state = state,
                    isBluetoothOn = deviceManager.isBluetoothEnabled(),
                    isConnecting = isConnecting,
                    onConnectClick = {
                        if (!btnLock) {
                            btnLock = true
                            val last = deviceManager.lastConnectedDevice
                            if (last != null) {
                                scope.launch {
                                    try {
                                        FreeBudsApp.instance.preferences.setLastDeviceAddress(last.address)
                                        deviceManager.connect(last)
                                    } catch (e: Exception) {
                                        Log.e("MainScreen", "Reconnect failed", e)
                                        snackbarHostState.showSnackbar("${stringResource(R.string.btn_reconnect)} failed")
                                    }
                                    delay(500); btnLock = false
                                }
                            } else {
                                showDevicePicker = true
                                scope.launch { delay(500); btnLock = false }
                            }
                        }
                    },
                    onDisconnectClick = {
                        if (!btnLock) {
                            btnLock = true
                            deviceManager.disconnect()
                            scope.launch { delay(500); btnLock = false }
                        }
                    }
                )
            }

            // Battery card (only when connected)
            if (state.connected) {
                item {
                        BatteryCard(
                            batteryLeft = state.batteryLeft,
                            batteryRight = state.batteryRight,
                            batteryCase = state.batteryCase,
                            batteryChargingLeft = state.batteryChargingLeft,
                            batteryChargingRight = state.batteryChargingRight,
                            batteryChargingCase = state.batteryChargingCase
                    )
                }
            }

            // Quick controls (only when connected)
            if (state.connected) {
                item {
                    val isFixed by FreeBudsApp.instance.preferences.lowLatencyAutoOn.collectAsState(initial = false)
                    QuickControlsCard(
                        state = state,
                        isLowLatencyFixed = isFixed,
                        onSetAncMode = { mode ->
                            scope.launch { deviceManager.setProperty("anc_mode", mode) }
                        },
                        onToggleLowLatency = { enabled ->
                            scope.launch {
                                FreeBudsApp.instance.preferences.setLowLatencyAutoOn(false)
                                deviceManager.setProperty("low_latency", enabled.toString())
                            }
                        },
                        onSetLowLatencyFixed = {
                            scope.launch {
                                FreeBudsApp.instance.preferences.setLowLatencyAutoOn(true)
                                deviceManager.setProperty("low_latency", "true")
                            }
                        },
                        onSetSoundQuality = { sq ->
                            scope.launch { deviceManager.setProperty("sound_quality", sq) }
                        }
                    )
                }

                // Equalizer preset selector
                item {
                    EqPresetCard(
                        currentPreset = state.eqPreset,
                        presetOptions = state.eqPresetOptions,
                        onSelectPreset = { preset ->
                            scope.launch { deviceManager.setProperty("eq_preset", preset) }
                        }
                    )
                }

                // Gesture settings
                item {
                    GestureSettingsCard(
                        doubleTapLeft = state.doubleTapLeft,
                        doubleTapRight = state.doubleTapRight,
                        onSetLeft = { action ->
                            scope.launch { deviceManager.setProperty("double_tap_left", action) }
                        },
                        onSetRight = { action ->
                            scope.launch { deviceManager.setProperty("double_tap_right", action) }
                        }
                    )
                }

                // Dual-connect toggle
                item {
                    DualConnectCard(
                        enabled = state.dualConnectEnabled,
                        onToggle = { enabled ->
                            scope.launch { deviceManager.setProperty("dual_connect_enabled", enabled.toString()) }
                        }
                    )
                }

                // Device info
                item {
                    DeviceInfoCard(
                        firmwareVersion = state.firmwareVersion,
                        serialNumber = state.serialNumber,
                        hardwareVersion = state.hardwareVersion
                    )
                }
            }
        }
    }

    // Device picker dialog
    if (showDevicePicker) {
        DevicePickerDialog(
            deviceManager = deviceManager,
            onDismiss = { showDevicePicker = false },
            onDeviceSelected = { device ->
                if (!btnLock) {
                    btnLock = true
                    showDevicePicker = false
                    scope.launch {
                        try {
                            FreeBudsApp.instance.preferences.setLastDeviceAddress(device.address)
                            deviceManager.connect(device)
                        } catch (e: Exception) {
                            Log.e("MainScreen", "connect() failed", e)
                            snackbarHostState.showSnackbar(
                                "${stringResource(R.string.status_connected)} failed: ${e.message ?: "Unknown error"}"
                            )
                        }
                        delay(500); btnLock = false
                    }
                }
            }
        )
    }
}

@Composable
private fun ConnectionCard(
    state: DeviceState,
    isBluetoothOn: Boolean,
    isConnecting: Boolean,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (state.connected) Icons.Filled.Bluetooth
                              else Icons.Filled.BluetoothDisabled,
                contentDescription = null,
                tint = if (state.connected) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        isConnecting -> stringResource(R.string.status_connecting)
                        state.connected -> state.deviceName.ifEmpty { stringResource(R.string.status_connected) }
                        state.lastDeviceName != null -> "${stringResource(R.string.status_not_connected)}: ${state.lastDeviceName}"
                        else -> stringResource(R.string.status_not_connected)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (state.lastDeviceName != null && !state.connected && !isConnecting)
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when {
                        isConnecting -> state.deviceName.ifEmpty { stringResource(R.string.status_connecting) }
                        state.connected -> state.deviceAddress.ifEmpty { stringResource(R.string.status_ready) }
                        !state.connected && state.lastDeviceAddress != null -> state.lastDeviceAddress!!
                        !isBluetoothOn -> stringResource(R.string.bluetooth_off)
                        else -> stringResource(R.string.tap_to_connect)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = if (state.lastDeviceName != null && !state.connected && !isConnecting) 0.5f else 1f
                    )
                )
            }
            when {
                state.connected -> {
                    FilledTonalButton(onClick = onDisconnectClick) {
                        Text(stringResource(R.string.btn_disconnect))
                    }
                }
                isConnecting -> {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
                isBluetoothOn && state.lastDeviceName != null -> {
                    // Re-connect button when disconnected with last device known
                    FilledTonalButton(onClick = onConnectClick) {
                        Icon(Icons.Filled.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.btn_reconnect))
                    }
                }
                isBluetoothOn -> {
                    FilledTonalButton(onClick = onConnectClick) {
                        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.btn_connect))
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickControlsCard(
    state: DeviceState,
    isLowLatencyFixed: Boolean = false,
    onSetAncMode: (String) -> Unit,
    onToggleLowLatency: (Boolean) -> Unit,
    onSetLowLatencyFixed: () -> Unit,
    onSetSoundQuality: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = stringResource(R.string.section_quick_controls),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            // ANC Mode — compare with AncMode enum, not displayName string
            Text(stringResource(R.string.label_noise_control), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = state.ancMode == com.freebuds.controller.device.AncMode.OFF,
                    onClick = { onSetAncMode("off") },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                ) { Text(stringResource(R.string.label_normal)) }
                SegmentedButton(
                    selected = state.ancMode == com.freebuds.controller.device.AncMode.CANCELLATION,
                    onClick = { onSetAncMode("cancellation") },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                ) { Text(stringResource(R.string.label_noise_cancel)) }
                SegmentedButton(
                    selected = state.ancMode == com.freebuds.controller.device.AncMode.AWARENESS,
                    onClick = { onSetAncMode("awareness") },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                ) { Text(stringResource(R.string.label_awareness)) }
            }
            Spacer(Modifier.height(16.dp))

            // Low Latency — tri-state Manual / Off / Auto-Fixed
            Text(stringResource(R.string.label_game_mode), style = MaterialTheme.typography.labelLarge)
            Text(
                stringResource(R.string.label_game_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = state.lowLatency == false,
                    onClick = { onToggleLowLatency(false) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                ) { Text(stringResource(R.string.btn_off)) }
                SegmentedButton(
                    selected = state.lowLatency == true,
                    onClick = { onToggleLowLatency(true) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                ) { Text(stringResource(R.string.btn_on)) }
                SegmentedButton(
                    selected = isLowLatencyFixed,
                    onClick = onSetLowLatencyFixed,
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                ) { Text(stringResource(R.string.btn_fixed_on)) }
            Spacer(Modifier.height(12.dp))

            // Sound Quality
            Text(stringResource(R.string.label_sound_quality), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = state.soundQuality != "sqp_quality",
                    onClick = { onSetSoundQuality("sqp_connectivity") },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text(stringResource(R.string.btn_stable)) }
                SegmentedButton(
                    selected = state.soundQuality == "sqp_quality",
                    onClick = { onSetSoundQuality("sqp_quality") },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text(stringResource(R.string.btn_quality)) }
            }
        }
    }
}

@Composable
private fun DevicePickerDialog(
    deviceManager: DeviceManager,
    onDismiss: () -> Unit,
    onDeviceSelected: (BluetoothDevice) -> Unit
) {
    val context = LocalContext.current
    val hasBluetoothConnect = ContextCompat.checkSelfPermission(
        context, Manifest.permission.BLUETOOTH_CONNECT
    ) == PackageManager.PERMISSION_GRANTED

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    val devices = remember(hasBluetoothConnect) {
        if (hasBluetoothConnect) deviceManager.findPairedDevices() else emptyList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            if (devices.isEmpty() && hasBluetoothConnect) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.dlg_ok)) }
            }
        },
        dismissButton = {
            if (devices.isNotEmpty() || !hasBluetoothConnect) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.dlg_cancel)) }
            }
        },
        title = { Text(stringResource(R.string.dlg_select_device)) },
        text = {
            if (!hasBluetoothConnect) {
                Column {
                    Text(stringResource(R.string.dlg_permission_required), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = {
                        permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN))
                    }) { Text(stringResource(R.string.dlg_permission_grant)) }
                }
            } else if (devices.isEmpty()) {
                Text(stringResource(R.string.dlg_no_devices))
            } else {
                LazyColumn {
                    items(devices) { device ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { onDeviceSelected(device) },
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(device.name ?: stringResource(R.string.dlg_no_devices), style = MaterialTheme.typography.bodyLarge)
                                    Text(device.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun EqPresetCard(
    currentPreset: String,
    presetOptions: List<String>,
    onSelectPreset: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(stringResource(R.string.section_eq), style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            if (presetOptions.isEmpty()) {
                Text(
                    if (currentPreset.isNotEmpty()) currentPreset else stringResource(R.string.label_loading),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                // Show current preset as label
                Text(stringResource(R.string.label_current, currentPreset), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                // Wrap buttons in a flow-like row
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    var rowItems = mutableListOf<String>()
                    for (option in presetOptions) {
                        rowItems.add(option)
                        if (rowItems.size >= 3) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                rowItems.forEach { opt ->
                                    FilterChip(
                                        selected = opt == currentPreset,
                                        onClick = { onSelectPreset(opt) },
                                        label = { Text(opt) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            rowItems.clear()
                        }
                    }
                    if (rowItems.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            rowItems.forEach { opt ->
                                FilterChip(
                                    selected = opt == currentPreset,
                                    onClick = { onSelectPreset(opt) },
                                    label = { Text(opt) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GestureSettingsCard(
    doubleTapLeft: String,
    doubleTapRight: String,
    onSetLeft: (String) -> Unit,
    onSetRight: (String) -> Unit
) {
    val actions = listOf(
        stringResource(R.string.gesture_off),
        stringResource(R.string.gesture_play_pause),
        stringResource(R.string.gesture_next_track),
        stringResource(R.string.gesture_prev_track),
        stringResource(R.string.gesture_assistant)
    )
    var showLeftPicker by remember { mutableStateOf(false) }
    var showRightPicker by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(stringResource(R.string.section_gestures), style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            // Left
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showLeftPicker = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.label_left), style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f))
                Text(doubleTapLeft.ifEmpty { stringResource(R.string.gesture_off) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(8.dp))

            // Right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showRightPicker = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.label_right), style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f))
                Text(doubleTapRight.ifEmpty { stringResource(R.string.gesture_off) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    // Picker dialogs
    if (showLeftPicker) {
        GesturePickerDialog(
            title = stringResource(R.string.dlg_left_double_tap),
            actions = actions,
            current = doubleTapLeft,
            onSelect = { onSetLeft(it); showLeftPicker = false },
            onDismiss = { showLeftPicker = false }
        )
    }
    if (showRightPicker) {
        GesturePickerDialog(
            title = stringResource(R.string.dlg_right_double_tap),
            actions = actions,
            current = doubleTapRight,
            onSelect = { onSetRight(it); showRightPicker = false },
            onDismiss = { showRightPicker = false }
        )
    }
}

@Composable
private fun GesturePickerDialog(
    title: String,
    actions: List<String>,
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dlg_cancel)) } },
        title = { Text(title) },
        text = {
            LazyColumn {
                items(actions) { action ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(action) },
                        color = if (action == current)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = action == current,
                                onClick = { onSelect(action) }
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(action, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun DualConnectCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.section_dual_connect), style = MaterialTheme.typography.titleSmall)
                Text(
                    if (enabled) stringResource(R.string.dual_enabled)
                    else stringResource(R.string.dual_disabled),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun DeviceInfoCard(
    firmwareVersion: String,
    serialNumber: String,
    hardwareVersion: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(stringResource(R.string.section_device_info), style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            if (firmwareVersion.isNotEmpty()) {
                InfoRow(stringResource(R.string.info_firmware), firmwareVersion)
            }
            if (serialNumber.isNotEmpty()) {
                InfoRow(stringResource(R.string.info_serial), serialNumber)
            }
            if (hardwareVersion.isNotEmpty()) {
                InfoRow(stringResource(R.string.info_hardware), hardwareVersion)
            }
            if (firmwareVersion.isEmpty() && serialNumber.isEmpty() && hardwareVersion.isEmpty()) {
                Text(stringResource(R.string.info_not_available), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium)
    }
}
