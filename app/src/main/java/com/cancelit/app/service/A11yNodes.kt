package com.cancelit.app.service

import android.view.accessibility.AccessibilityNodeInfo

// Helpers for reading other apps' screens. Every walk is depth- and size-capped, so a deep or
// huge view tree can't stall the accessibility event thread.

/** The text and content descriptions under this node, space-separated. */
internal fun AccessibilityNodeInfo.visibleText(maxChars: Int, maxDepth: Int): String =
    buildString { this@visibleText.collectText(this, 0, maxChars, maxDepth) }.trim()

private fun AccessibilityNodeInfo.collectText(out: StringBuilder, depth: Int, maxChars: Int, maxDepth: Int) {
    if (depth > maxDepth || out.length >= maxChars) return
    text?.takeIf { it.isNotBlank() }?.let { out.append(it).append(' ') }
    contentDescription?.takeIf { it.isNotBlank() }?.let { out.append(it).append(' ') }
    for (i in 0 until childCount) {
        if (out.length >= maxChars) return
        if (runCatching { getChild(i)?.collectText(out, depth + 1, maxChars, maxDepth) }.isFailure) return
    }
}