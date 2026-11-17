package com.cancelit.app.data

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.cancelit.app.ai.AI_USAGE_PREFS
import com.cancelit.app.ai.SMART_KEYWORDS_PREFS
import com.cancelit.app.ai.SmartKeywords

/** Every SharedPreferences file CancelIt writes. Add new files here so "Delete all my data" clears them. */
private val APP_PREFS_FILES = FOCUS_PREFS_FILES + listOf(SMART_KEYWORDS_PREFS, AI_USAGE_PREFS)

/** Erases everything CancelIt stored on this phone, including what's only held in memory. The
 *  caller restarts the screens afterwards. */
internal fun deleteAllData(context: Context) {
    APP_PREFS_FILES.forEach { name ->
        context.getSharedPreferences(name, Context.MODE_PRIVATE).edit(commit = true) { clear() }
    }
    FocusStore.forgetUnsaved()
    SmartKeywords.clearCache()
    NotificationManagerCompat.from(context).cancelAll()
}
