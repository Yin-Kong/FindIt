package com.findit.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModelProvider
import com.findit.app.ui.FindItApp
import com.findit.app.ui.theme.FindItTheme

val LocalViewModelFactory = staticCompositionLocalOf<ViewModelProvider.Factory> {
    error("ViewModelFactory not provided")
}

class MainActivity : ComponentActivity() {
    private val pendingJsonState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingJsonState.value = extractJsonFromIntent(intent)

        val app = application as FindItApplication
        val factory = FindItViewModelFactory(
            application = app,
            itemRepository = app.itemRepository,
            locationRepository = app.locationRepository,
            batchRepository = app.batchRepository
        )

        setContent {
            FindItTheme {
                CompositionLocalProvider(LocalViewModelFactory provides factory) {
                    FindItApp(
                        pendingJson = pendingJsonState.value,
                        onPendingJsonConsumed = { pendingJsonState.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingJsonState.value = extractJsonFromIntent(intent)
    }

    private fun extractJsonFromIntent(intent: Intent?): String? {
        val data = intent?.data ?: return null
        return data.getQueryParameter("json")
            ?: data.getQueryParameter("payload")
            ?: data.getQueryParameter("data")
            ?: data.lastPathSegment?.takeIf { data.host == "json" }
    }
}
