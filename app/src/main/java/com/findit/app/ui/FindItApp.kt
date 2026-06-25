package com.findit.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.findit.app.ui.navigation.AppNavGraph
import com.findit.app.ui.navigation.Screen
import com.findit.app.ui.navigation.bottomNavItems

@Composable
fun FindItApp(
    pendingJson: String? = null,
    onPendingJsonConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var chromeCollapseFraction by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(pendingJson) {
        if (!pendingJson.isNullOrBlank()) {
            navController.navigate(Screen.BatchImport.route) {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(currentRoute) {
        if (currentRoute != Screen.Home.route) {
            chromeCollapseFraction = 0f
        }
    }

    val showBottomBar = bottomNavItems.any { it.route == currentRoute }
    val bottomBarHeight by animateDpAsState(
        targetValue = if (showBottomBar) 80.dp * (1f - chromeCollapseFraction) else 0.dp,
        label = "bottomBarHeight"
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(bottomBarHeight)
                        .clipToBounds()
                ) {
                    NavigationBar(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                alpha = 1f - chromeCollapseFraction
                                translationY = 80.dp.toPx() * chromeCollapseFraction
                            }
                    ) {
                        bottomNavItems.forEach { screen ->
                            NavigationBarItem(
                                icon = { screen.icon?.let { Icon(it, contentDescription = screen.title) } },
                                label = { Text(screen.title) },
                                selected = currentRoute == screen.route,
                                onClick = {
                                    if (currentRoute != screen.route) {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        androidx.compose.foundation.layout.Box(modifier = Modifier.padding(innerPadding)) {
            AppNavGraph(
                navController = navController,
                pendingJson = pendingJson,
                onPendingJsonConsumed = onPendingJsonConsumed,
                homeChromeCollapseFraction = chromeCollapseFraction,
                onHomeChromeCollapseFractionChange = { chromeCollapseFraction = it }
            )
        }
    }
}
