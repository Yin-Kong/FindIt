package com.findit.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.findit.app.ui.batch.BatchImportScreen
import com.findit.app.ui.help.HelpScreen
import com.findit.app.ui.home.HomeScreen
import com.findit.app.ui.item.AddEditItemScreen
import com.findit.app.ui.item.ItemDetailScreen
import com.findit.app.ui.location.LocationScreen

@Composable
fun AppNavGraph(
    navController: NavHostController,
    pendingJson: String? = null,
    onPendingJsonConsumed: () -> Unit = {},
    homeChromeCollapseFraction: Float = 0f,
    onHomeChromeCollapseFractionChange: (Float) -> Unit = {}
) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {

        composable(Screen.Home.route) {
            HomeScreen(
                onItemClick = { itemId ->
                    navController.navigate(Screen.ItemDetail.createRoute(itemId))
                },
                onAddClick = {
                    navController.navigate(Screen.AddItem.route)
                },
                onBatchImportClick = {
                    navController.navigate(Screen.BatchImport.route)
                },
                onHelpClick = {
                    navController.navigate(Screen.Help.route)
                },
                chromeCollapseFraction = homeChromeCollapseFraction,
                onChromeCollapseFractionChange = onHomeChromeCollapseFractionChange
            )
        }

        composable(Screen.AddItem.route) {
            AddEditItemScreen(
                itemId = null,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.EditItem.route,
            arguments = listOf(navArgument("itemId") { type = NavType.LongType })
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getLong("itemId") ?: return@composable
            AddEditItemScreen(
                itemId = itemId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.ItemDetail.route,
            arguments = listOf(navArgument("itemId") { type = NavType.LongType })
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getLong("itemId") ?: return@composable
            ItemDetailScreen(
                itemId = itemId,
                onNavigateBack = { navController.popBackStack() },
                onEditClick = {
                    navController.navigate(Screen.EditItem.createRoute(itemId))
                }
            )
        }

        composable(Screen.Locations.route) {
            LocationScreen()
        }

        composable(Screen.BatchImport.route) {
            BatchImportScreen(
                onNavigateBack = { navController.popBackStack() },
                initialJson = pendingJson,
                onInitialJsonConsumed = onPendingJsonConsumed
            )
        }

        composable(Screen.Help.route) {
            HelpScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
