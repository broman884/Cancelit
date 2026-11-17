package com.cancelit.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.cancelit.app.MainActivity
import com.cancelit.app.data.FocusStore
import com.cancelit.app.data.PRESENCE_TIMEOUT_MS
import com.cancelit.app.describeDuration
import com.cancelit.app.formatDuration
import kotlin.math.abs

private const val GRACE_CHANNEL_ID = "cancelit_grace_timer"
private const val GRACE_ALERT_CHANNEL_ID = "cancelit_grace_alerts"
private const val PRESENCE_CHANNEL_ID = "cancelit_presence"
private const val PRESENCE_NOTIFICATION_ID = 4711
private const val GRACE_TIMER_NOTIFICATION_ID = 4712
private const val GRACE_WARNING_NOTIFICATION_ID = 4713
private const val LOCK_NOTIFICATION_BASE_ID = 4800

/** The "Yes, I'm here" button on the check-in notification. Only the service's own, unexported
 *  receiver listens for it, so nothing outside the app can fake a confirmation. */
internal const val ACTION_PRESENCE_ACK = "com.cancelit.app.action.PRESENCE_ACK"

/** The id of [pkg]'s unlock countdown, so the emergency unlock can remove it. */
internal fun lockNotificationId(pkg: String) = LOCK_NOTIFICATION_BASE_ID + (pkg.hashCode() and 0x3FF)

/**
 * The service's notifications: the live time-left countdown, the "almost out" heads-up, each
 * locked app's unlock countdown, and the "Are you there?" check-in. Posting never fails loudly:
 * with notifications denied, the overlay and the in-app screens still work.
 */
internal class FocusNotifications(
    private val context: Context,
    private val store: FocusStore,
    private val appLabel: (String) -> String
) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    // Time-left countdown bookkeeping (only touched from the grace loop).
    private var timerPkg: String? = null
    private var timerExpiry = 0L

    init {
        createChannels()
    }

    private fun createChannels() {
        val channels = listOf(
            NotificationChannel(GRACE_CHANNEL_ID, "Grace timer", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Live countdown of how much time you have left in a protected app."
                setShowBadge(false)
            },
            NotificationChannel(GRACE_ALERT_CHANNEL_ID, "Grace warnings and locks", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Heads-up when you're almost out of time, and when an app gets locked."
                enableVibration(true)
            },
            NotificationChannel(PRESENCE_CHANNEL_ID, "Study presence checks", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Asks whether you're still at your study session."
                enableVibration(true)
                setShowBadge(true)
            }
        )
        // Creating an existing channel again leaves the user's settings for it alone.
        runCatching { manager?.createNotificationChannels(channels) }
    }

    /**
     * The time left in [pkg]. The chronometer counts down by itself, so this only re-posts when
     * the timer is new (a different app, or the user just came back) or has drifted, instead of
     * every second.
     */
    fun showTimeLeft(pkg: String, remainingMs: Long, now: Long) {
        val expiry = now + remainingMs
        if (timerPkg == pkg && abs(expiry - timerExpiry) < 2_000L) return
        timerPkg = pkg
        timerExpiry = expiry
        val notification = Notification.Builder(context, GRACE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("${appLabel(pkg)} · time left")
            .setContentText("When this hits zero it locks for ${describeDuration(store.lockdownMs())}.")
            .setCategory(Notification.CATEGORY_STATUS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setWhen(expiry)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setContentIntent(openAppIntent(2))
            .build()
        post(GRACE_TIMER_NOTIFICATION_ID, notification)
    }

    fun clearTimeLeft() {
        if (timerPkg == null) return
        timerPkg = null
        timerExpiry = 0L
        cancel(GRACE_TIMER_NOTIFICATION_ID)
    }

    /** One-time heads-up when the allowance is nearly gone. Removes itself when time is up. */
    fun warnAlmostOut(pkg: String, remainingMs: Long) {
        val notification = Notification.Builder(context, GRACE_ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("${appLabel(pkg)}: almost out of time")
            .setContentText("Only ${formatDuration(remainingMs)} left, then it locks for ${describeDuration(store.lockdownMs())}.")
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setTimeoutAfter(remainingMs.coerceAtLeast(1_000L))
            .setContentIntent(openAppIntent(3))
            .build()
        post(GRACE_WARNING_NOTIFICATION_ID, notification)
    }

    /** An unlock countdown for a locked app; it dismisses itself when the lock ends. */
    fun showLocked(pkg: String, until: Long) {
        val notification = Notification.Builder(context, GRACE_ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("${appLabel(pkg)} is locked")
            .setContentText("Unlocks when the timer ends.")
            .setCategory(Notification.CATEGORY_STATUS)
            .setOngoing(true)
            .setShowWhen(true)
            .setWhen(until)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setTimeoutAfter((until - System.currentTimeMillis()).coerceAtLeast(1_000L))
            .setContentIntent(openAppIntent(4))
            .build()
        post(lockNotificationId(pkg), notification)
    }

    /** "Are you there?" with a "Yes, I'm here" button. If notifications are off, the service
     *  shows the question on screen instead (see [canShowPresence]). */
    fun askAreYouThere() {
        val ackIntent = PendingIntent.getBroadcast(
            context, 0, Intent(ACTION_PRESENCE_ACK).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(context, PRESENCE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Are you there?")
            .setContentText("Confirm within 1 minute or CancelIt closes your apps.")
            .setCategory(Notification.CATEGORY_REMINDER)
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(PRESENCE_TIMEOUT_MS)
            .setContentIntent(openAppIntent(1))
            .addAction(Notification.Action.Builder(null, "Yes, I'm here", ackIntent).build())
            .build()
        post(PRESENCE_NOTIFICATION_ID, notification)
    }

    fun cancelAreYouThere() = cancel(PRESENCE_NOTIFICATION_ID)

    /** False when the check-in can't appear as a notification: notifications are off for the
     *  app, or the check-in channel was turned off. The service then asks on screen instead. */
    fun canShowPresence(): Boolean = runCatching {
        val m = manager ?: return@runCatching false
        m.areNotificationsEnabled() &&
                m.getNotificationChannel(PRESENCE_CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
    }.getOrDefault(false)

    private fun openAppIntent(requestCode: Int): PendingIntent = PendingIntent.getActivity(
        context, requestCode,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun post(id: Int, notification: Notification) {
        try {
            manager?.notify(id, notification)
        } catch (e: Exception) {
            Log.w("CancelIt-Notify", "Could not post notification: ${e.message}")
        }
    }

    /** Cancelling is never critical, so a failure here is ignored. */
    private fun cancel(id: Int) {
        runCatching { manager?.cancel(id) }
    }
}
