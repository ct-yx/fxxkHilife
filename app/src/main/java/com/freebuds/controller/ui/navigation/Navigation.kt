package com.freebuds.controller.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.freebuds.controller.ui.screen.MainScreen
import com.freebuds.controller.ui.screen.SettingsScreen
import com.freebuds.controller.device.DeviceManager

object Routes {
    const val MAIN = "main"
    const val SETTINGS = "settings"
}

@Composable
fun FreeBudsNavHost(
    navController: NavHostController,
    deviceManager: DeviceManager
) {
    NavHost(
        navController = navController,
        startDestination = Routes.MAIN
    ) {
        composable(Routes.MAIN) {
            MainScreen(
                deviceManager = deviceManager,
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                deviceManager = deviceManager,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
