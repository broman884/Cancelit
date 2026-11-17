package com.cancelit.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** Runs [action] each time the screen comes back to the front, for example after the user
 *  changed a permission in system Settings, which doesn't recompose anything by itself. */
@Composable
internal fun OnResume(action: () -> Unit) {
    @Suppress("DEPRECATION") val owner = LocalLifecycleOwner.current
    val latestAction by rememberUpdatedState(action)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) latestAction()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
