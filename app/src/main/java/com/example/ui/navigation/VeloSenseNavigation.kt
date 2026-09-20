package com.example.ui.navigation

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.service.CyclingTrackingService
import com.example.ui.screens.coach.CoachScreen
import com.example.ui.screens.history.HistoryScreen
import com.example.ui.screens.history.RideDetailScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.ride.ActiveRideScreen
import com.example.ui.screens.ride.PreRideScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.theme.*
import com.example.ui.viewmodels.*

sealed class Screen(val route: String, val title: String, val icon: ImageVector? = null) {
    object Home : Screen("home", "Inicio", Icons.AutoMirrored.Filled.DirectionsBike)
    object PreRide : Screen("pre_ride", "Preparar")
    object ActiveRide : Screen("active_ride", "En Ruta")
    object History : Screen("history", "Historial", Icons.Default.History)
    object RideDetail : Screen("ride_detail/{rideId}", "Detalle") {
        fun createRoute(rideId: Long) = "ride_detail/$rideId"
    }
    object Coach : Screen("coach", "Coach", Icons.Default.Psychology)
    object Settings : Screen("settings", "Ajustes", Icons.Default.Settings)
}

val bottomNavScreens = listOf(
    Screen.Home,
    Screen.History,
    Screen.Coach,
    Screen.Settings
)

@Composable
fun VeloSenseAppNav() {
    MainAppNavigation()
}

@Composable
fun MainAppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Check if tracking is active to auto-route or show indicator
    val trackingState by CyclingTrackingService.rideState.collectAsState()

    val showBottomBar = currentRoute != Screen.ActiveRide.route

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = VeloDarkSurface,
                    contentColor = VeloTextPrimary,
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets.navigationBars.union(WindowInsets.displayCutout)
                        .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            drawLine(
                                color = VeloDarkCardBorder,
                                start = Offset.Zero,
                                end = Offset(size.width, 0f),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                ) {
                    bottomNavScreens.forEach { screen ->
                        val isSelected = currentRoute == screen.route
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    screen.icon ?: Icons.Default.Circle,
                                    contentDescription = screen.title,
                                    tint = if (isSelected) ElectricLime else VeloTextSecondary
                                )
                            },
                            label = {
                                Text(
                                    screen.title,
                                    style = VeloTypography.labelSmall,
                                    color = if (isSelected) ElectricLime else VeloTextSecondary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            selected = isSelected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.testTag("nav_item_${screen.route}")
                        )
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = VeloDarkBg
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = if (trackingState.isTracking) Screen.ActiveRide.route else Screen.Home.route,
                modifier = Modifier.fillMaxSize()
            ) {
            composable(Screen.Home.route) {
                val homeVm: HomeViewModel = viewModel()
                HomeScreen(
                    viewModel = homeVm,
                    onStartRideClicked = {
                        if (trackingState.isTracking) {
                            navController.navigate(Screen.ActiveRide.route)
                        } else {
                            navController.navigate(Screen.PreRide.route)
                        }
                    },
                    onRideClicked = { rideId ->
                        navController.navigate(Screen.RideDetail.createRoute(rideId))
                    },
                    onViewHistoryClicked = {
                        navController.navigate(Screen.History.route)
                    }
                )
            }

            composable(Screen.PreRide.route) {
                val rideVm: RideViewModel = viewModel()
                PreRideScreen(
                    viewModel = rideVm,
                    onBackClicked = { navController.popBackStack() },
                    onRideStarted = {
                        navController.navigate(Screen.ActiveRide.route) {
                            popUpTo(Screen.Home.route)
                        }
                    }
                )
            }

            composable(Screen.ActiveRide.route) {
                val rideVm: RideViewModel = viewModel()
                ActiveRideScreen(
                    viewModel = rideVm,
                    onRideFinished = { savedRideId ->
                        navController.navigate(Screen.RideDetail.createRoute(savedRideId)) {
                            popUpTo(Screen.Home.route)
                        }
                    }
                )
            }

            composable(Screen.History.route) {
                val historyVm: HistoryViewModel = viewModel()
                HistoryScreen(
                    viewModel = historyVm,
                    onRideClicked = { rideId ->
                        navController.navigate(Screen.RideDetail.createRoute(rideId))
                    }
                )
            }

            composable(
                route = Screen.RideDetail.route,
                arguments = listOf(navArgument("rideId") { type = NavType.LongType })
            ) { backStackEntry ->
                val rideId = backStackEntry.arguments?.getLong("rideId") ?: 0L
                val historyVm: HistoryViewModel = viewModel()
                RideDetailScreen(
                    rideId = rideId,
                    viewModel = historyVm,
                    onBackClicked = { navController.popBackStack() },
                    onConsultCoachClicked = {
                        navController.navigate(Screen.Coach.route)
                    }
                )
            }

            composable(Screen.Coach.route) {
                val coachVm: CoachViewModel = viewModel()
                CoachScreen(viewModel = coachVm)
            }

            composable(Screen.Settings.route) {
                val settingsVm: SettingsViewModel = viewModel()
                SettingsScreen(viewModel = settingsVm)
            }
        }
    }
}
}
