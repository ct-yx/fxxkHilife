package com.freebuds.controller.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.freebuds.controller.data.DeviceViewModel
import com.freebuds.controller.ui.theme.FxxkHilifeTheme
import com.freebuds.controller.ui.theme.ThemeMode
import com.freebuds.controller.ui.theme.loadThemeMode

class MainActivity : ComponentActivity() {

    val viewModel: DeviceViewModel by viewModels()
    private var themeMode by mutableStateOf(ThemeMode.SYSTEM)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 加载主题偏好
        themeMode = loadThemeMode(this)

        // 前后台感知
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.setAppInForeground(true)
                Lifecycle.Event.ON_PAUSE -> viewModel.setAppInForeground(false)
                else -> {}
            }
        })

        setContent {
            FxxkHilifeTheme(mode = themeMode) {
                AppNavHost(
                    viewModel = viewModel,
                    onOpenTerminal = {
                        startActivity(Intent(this, TerminalActivity::class.java))
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        viewModel.stopScan()
        super.onDestroy()
    }
}
