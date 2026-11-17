package com.cancelit.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.cancelit.app.R
import com.cancelit.app.data.GRACE_WARNING_MS
import com.cancelit.app.formatClock

// The windows the accessibility service draws over other apps. WindowManager may only be used
// from the main thread, and the service's loops run on background threads, so every public call
// here hops to the main thread first.

/** A block message clears itself after this long; the lock itself keeps being enforced. */
private const val BLOCK_AUTO_DISMISS_MS = 12_000L
/** An unanswered question clears itself after this long, without locking anything. */
private const val QUESTION_TIMEOUT_MS = 60_000L

private val mainHandler = Handler(Looper.getMainLooper())

private fun runOnMain(block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
}

private fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

private fun Context.windowManager() = getSystemService(Context.WINDOW_SERVICE) as WindowManager

private class Choice(val label: String, val onClick: () -> Unit)

/**
 * The full-screen overlay: a block message with a "Go home" button, or a question about
 * something the AI flagged, where nothing is locked unless the user confirms. Only one shows at
 * a time, and a waiting question is never replaced by a block message. Either one clears itself,
 * so a problem can never strand the user on a black screen.
 */
internal class BlockOverlay(private val service: AccessibilityService) {
    private var view: View? = null
    private var titleView: TextView? = null
    private var bodyView: TextView? = null
    /** True while a question waits for an answer. Main thread only. */
    private var asking = false
    private val autoDismiss: Runnable = Runnable { dismiss() }

    /**
     * Shows a block message, or updates the one already showing. With [replace] false, a message
     * with a different title that's already on screen is left alone: the enforcement loop's
     * generic "Locked down" mustn't overwrite the "You typed …" explanation a moment after it
     * appears (it still refreshes its own countdown).
     */
    fun showBlock(title: String, message: String, replace: Boolean = true): Unit = runOnMain {
        if (asking) return@runOnMain
        if (view != null) {
            if (!replace && titleView?.text?.toString() != title) return@runOnMain
            titleView?.text = title
            bodyView?.text = message
            dismissAfter(BLOCK_AUTO_DISMISS_MS)
            return@runOnMain
        }
        val goHome = Choice(service.getString(R.string.go_home)) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        }
        if (add(title, message, listOf(goHome))) dismissAfter(BLOCK_AUTO_DISMISS_MS)
    }

    /** Asks [title] / [message] with two answers. Unanswered, it clears itself after
     *  [QUESTION_TIMEOUT_MS] without calling either. */
    fun ask(
        title: String,
        message: String,
        confirmLabel: String,
        dismissLabel: String,
        onConfirm: () -> Unit,
        onDismiss: () -> Unit
    ): Unit = runOnMain {
        dismiss()
        if (add(title, message, listOf(Choice(confirmLabel, onConfirm), Choice(dismissLabel, onDismiss)))) {
            asking = true
            dismissAfter(QUESTION_TIMEOUT_MS)
        }
    }

    fun dismiss(): Unit = runOnMain {
        mainHandler.removeCallbacks(autoDismiss)
        asking = false
        // Already detached (window token gone, service torn down) is fine: nothing to remove.
        view?.let { runCatching { service.windowManager().removeView(it) } }
        view = null
        titleView = null
        bodyView = null
    }

    private fun dismissAfter(ms: Long) {
        mainHandler.removeCallbacks(autoDismiss)
        mainHandler.postDelayed(autoDismiss, ms)
    }

    /** Builds the overlay and adds it. Returns false if Android refused the window. */
    private fun add(title: String, message: String, choices: List<Choice>): Boolean {
        fun text(value: String, size: Float) = TextView(service).apply {
            this.text = value
            textSize = size
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, service.dp(6), 0, service.dp(6))
        }

        val newTitle = text(title, 28f)
        val newBody = text(message, 17f)
        val layout = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            gravity = Gravity.CENTER
            val padding = service.dp(24)
            setPadding(padding, padding, padding, padding)
            addView(newTitle)
            addView(newBody)
            choices.forEach { choice ->
                addView(Button(service).apply {
                    text = choice.label
                    setOnClickListener {
                        dismiss()
                        choice.onClick()
                    }
                })
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE
        )
        return try {
            service.windowManager().addView(layout, params)
            view = layout
            titleView = newTitle
            bodyView = newBody
            true
        } catch (e: Exception) {
            Log.e("CancelIt-Overlay", "Could not show overlay: ${e.message}")
            false
        }
    }
}

/**
 * The small countdown pill pinned to the top of the screen over whatever app is in front. It's
 * touch-transparent, so it never gets in the way. Under a minute its border thickens; in the
 * last 10 seconds it blinks.
 */
internal class TimerChip(private val service: AccessibilityService) {
    /** Only touched on the main thread. */
    @Volatile private var chip: TextView? = null
    /** Which border the chip has now, so the drawable is only rebuilt when it changes. */
    private var urgent: Boolean? = null

    fun show(remainingMs: Long): Unit = runOnMain {
        val isUrgent = remainingMs <= GRACE_WARNING_MS
        val dim = remainingMs <= 10_000L && (remainingMs / 1000L) % 2L == 0L
        val view = chip ?: create()?.also { chip = it } ?: return@runOnMain
        view.text = formatClock(remainingMs)
        view.alpha = if (dim) 0.4f else 1f
        if (urgent != isUrgent) {
            view.background = GradientDrawable().apply {
                cornerRadius = service.dp(20).toFloat()
                setColor(0xE6000000.toInt())
                setStroke(service.dp(if (isUrgent) 2 else 1), Color.WHITE)
            }
            urgent = isUrgent
        }
    }

    fun hide() {
        if (chip == null) return
        runOnMain {
            val view = chip ?: return@runOnMain
            runCatching { service.windowManager().removeView(view) }
            chip = null
            urgent = null
        }
    }

    private fun create(): TextView? {
        val view = TextView(service).apply {
            typeface = Typeface.MONOSPACE
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(service.dp(14), service.dp(6), service.dp(14), service.dp(6))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = service.dp(8)
        }
        return try {
            service.windowManager().addView(view, params)
            view
        } catch (e: Exception) {
            Log.e("CancelIt-Chip", "Could not show timer chip: ${e.message}")
            null
        }
    }
}
