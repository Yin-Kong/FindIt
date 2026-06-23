package com.findit.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector? = null
) {
    data object Home : Screen("home", "物品列表", Icons.Default.Home)
    data object AddItem : Screen("add_item", "新增物品")
    data object EditItem : Screen("edit_item/{itemId}", "编辑物品") {
        fun createRoute(itemId: Long) = "edit_item/$itemId"
    }
    data object ItemDetail : Screen("item_detail/{itemId}", "物品详情") {
        fun createRoute(itemId: Long) = "item_detail/$itemId"
    }
    data object Locations : Screen("locations", "地点管理", Icons.Default.LocationOn)
    data object BatchImport : Screen("batch_import", "AI整理")
    data object Help : Screen("help", "使用说明")
}

val bottomNavItems = listOf(Screen.Home, Screen.Locations)
