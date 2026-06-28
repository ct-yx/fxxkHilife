package com.freebuds.controller.ui

import androidx.compose.runtime.*
import com.freebuds.controller.data.ConnectionState
import com.freebuds.controller.data.DeviceViewModel

@Composable
fun AppNavHost(viewModel: DeviceViewModel, onOpenTerminal: () -> Unit) {
    val connState by viewModel.connectionState.collectAsState()

    when (connState) {
        is ConnectionState.Connected -> DeviceScreen(
            viewModel = viewModel,
            onOpenTerminal = onOpenTerminal,
        )
        else -> ScanScreen(viewModel = viewModel)
    }
}
