package com.cancelit.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import com.cancelit.app.ai.GeminiClient
import com.cancelit.app.data.FocusStore
import com.cancelit.app.ui.AppRoot

// Where things live:
//   data/     settings, logs and timing rules (FocusStore, FocusRules)
//   ai/       every Gemini call (GeminiClient) and the prompts built on it
//   service/  the accessibility service, its overlays and notifications
//   ui/       the design system, the app shell and one file per screen under ui/screens

class MainActivity : ComponentActivity() {
    // Registered before onCreate runs so the launcher is always in a valid state.
    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GeminiClient.init(this)
        ensureNotificationPermission()
        setContent {
            // One store for the lifetime of the screen, not a new one on every recomposition.
            val store = remember { FocusStore(this@MainActivity) }
            AppRoot(store, ::openAccessibilitySettings, this@MainActivity)
        }
    }

    /** Check-ins and lock countdowns are notifications, which need a runtime grant on API 33+. */
    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun openAccessibilitySettings() = startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}
