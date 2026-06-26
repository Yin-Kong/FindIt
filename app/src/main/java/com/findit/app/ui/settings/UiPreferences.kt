package com.findit.app.ui.settings

import android.content.Context

private const val UI_PREFS_NAME = "findit_ui_preferences"
private const val KEY_COLORFUL_TAG_MARKERS = "colorful_tag_markers"
private const val KEY_AUTO_HIDE_CHROME = "auto_hide_chrome"
private const val KEY_LIST_RENDER_MODE = "list_render_mode"
private const val KEY_AUTO_RENDER_THRESHOLD = "auto_render_threshold"

enum class ListRenderMode(val value: String, val label: String) {
    Auto("auto", "自动"),
    NormalScroll("normal_scroll", "普通滚动"),
    Lazy("lazy", "懒加载滚动");

    companion object {
        fun fromValue(value: String?): ListRenderMode {
            return entries.firstOrNull { it.value == value } ?: Auto
        }
    }
}

fun isColorfulTagMarkersEnabled(context: Context): Boolean {
    return context
        .getSharedPreferences(UI_PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_COLORFUL_TAG_MARKERS, true)
}

fun setColorfulTagMarkersEnabled(context: Context, enabled: Boolean) {
    context
        .getSharedPreferences(UI_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_COLORFUL_TAG_MARKERS, enabled)
        .apply()
}

fun isAutoHideChromeEnabled(context: Context): Boolean {
    return context
        .getSharedPreferences(UI_PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_AUTO_HIDE_CHROME, false)
}

fun setAutoHideChromeEnabled(context: Context, enabled: Boolean) {
    context
        .getSharedPreferences(UI_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_AUTO_HIDE_CHROME, enabled)
        .apply()
}

fun getListRenderMode(context: Context): ListRenderMode {
    val value = context
        .getSharedPreferences(UI_PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_LIST_RENDER_MODE, ListRenderMode.Auto.value)
    return ListRenderMode.fromValue(value)
}

fun setListRenderMode(context: Context, mode: ListRenderMode) {
    context
        .getSharedPreferences(UI_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_LIST_RENDER_MODE, mode.value)
        .apply()
}

fun getAutoRenderThreshold(context: Context): Int {
    return context
        .getSharedPreferences(UI_PREFS_NAME, Context.MODE_PRIVATE)
        .getInt(KEY_AUTO_RENDER_THRESHOLD, 64)
}

fun setAutoRenderThreshold(context: Context, threshold: Int) {
    context
        .getSharedPreferences(UI_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putInt(KEY_AUTO_RENDER_THRESHOLD, threshold.coerceAtLeast(1))
        .apply()
}
