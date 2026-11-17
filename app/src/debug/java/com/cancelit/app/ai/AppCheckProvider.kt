package com.cancelit.app.ai

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug builds aren't installed from Google Play, so Play Integrity can't vouch for them. The
 * debug provider prints a token to Logcat on first run; register it in the Firebase console
 * (App Check > Apps > Manage debug tokens) so a debug build can use Firebase AI Logic and
 * Firestore. Keep that token private: it lets any device pass App Check.
 */
internal fun appCheckProviderFactory(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
