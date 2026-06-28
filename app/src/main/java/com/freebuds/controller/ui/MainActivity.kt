package com.freebuds.controller.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.freebuds.controller.data.DeviceViewModel
import com.freebuds.controller.ui.theme.FxxkHilifeTheme
import com.freebuds.controller.ui.theme.ThemeMode
import com.freebuds.controller.ui.theme.loadThemeMode
import com.freebuds.controller.ui.theme.saveThemeMode

class MainActivity : ComponentActivity() {

    val viewModel: DeviceViewModel by viewModels()
    private var themeMode by mutableStateOf(ThemeMode.SYSTEM)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not, proceed */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 加载主题偏好
        themeMode = loadThemeMode(this)

        // 请求通知权限（Android 13+）
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

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
                    },
                    onThemeChange = { mode ->
                        themeMode = mode
                        saveThemeMode(this, mode)
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        viewModel.stopScan()
        super.onDestroy()
    }
}
