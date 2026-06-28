package com.freebuds.controller.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.freebuds.controller.data.DeviceViewModel
import com.freebuds.controller.ui.theme.FxxkHilifeTheme

class MainActivity : ComponentActivity() {

    val viewModel: DeviceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FxxkHilifeTheme {
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
