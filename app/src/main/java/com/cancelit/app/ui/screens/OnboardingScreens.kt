package com.cancelit.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cancelit.app.BuildConfig
import com.cancelit.app.data.AGE_ADULT
import com.cancelit.app.data.AGE_MINOR
import com.cancelit.app.ui.FullScreenNotice
import com.cancelit.app.ui.GroupCard
import com.cancelit.app.ui.InfoRow
import com.cancelit.app.ui.RowDivider
import com.cancelit.app.ui.SecondaryButton
import com.cancelit.app.ui.StyleHint

/**
 * Shown full-screen once on first launch (must accept to use the app) and reachable any time
 * after that from the Focus tab in read-only form. The wording mirrors what the rest of the app
 * actually does — nothing here is aspirational.
 */
@Composable
internal fun PrivacyPolicyScreen(
    readOnly: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onClose: () -> Unit = {}
) = FullScreenNotice(
    title = "Privacy policy",
    subtitle = if (readOnly) "How CancelIt handles your data." else "Please read before you continue.",
    primaryLabel = if (readOnly) "Close" else "I agree, continue",
    onPrimary = if (readOnly) onClose else onAccept,
    secondaryLabel = if (readOnly) null else "Decline and exit",
    onSecondary = onDecline
) {
    item {
        GroupCard {
            InfoRow(
                Icons.Outlined.Lock, "Kept on your phone",
                "Your blocked apps, keywords, activity log, Coach chat and settings are " +
                        "stored only in this app's local storage. CancelIt has no account and " +
                        "never sells your data or uses it for ads. You can erase it all with " +
                        "\"Delete all my data\" on the Focus tab."
            )
            RowDivider()
            InfoRow(
                Icons.Outlined.Shield, "Accessibility service",
                "To close and lock apps, and to detect blocked keywords as you type, " +
                        "CancelIt's accessibility service reads on-screen text and app names " +
                        "while it's turned on. This works only on this device and is never " +
                        "uploaded, except for the specific checks described below."
            )
            RowDivider()
            InfoRow(
                Icons.Outlined.AutoAwesome, "What's sent to Google's Gemini API",
                "Only if you're 18 or older, three features send short text snippets to " +
                        "Google's Gemini API for analysis: Study mode (recent on-screen text, to " +
                        "check it matches your topic), Smart keyword detection if you turn it on " +
                        "(short text you type elsewhere, to check it against your blocked " +
                        "topics), and the Coach chat (your recent messages plus a summary of your " +
                        "activity). Password fields are never read or " +
                        "sent. Requests go through Google's Firebase to Gemini's free service. " +
                        "CancelIt doesn't store them, but Google may use them to improve its " +
                        "products and its reviewers may read them, so don't send passwords, health " +
                        "or money details. Google's Gemini API terms and privacy policy apply. " +
                        "Firebase App Check (Google) confirms requests come from the genuine app. " +
                        "A Coach reply you report is kept for 90 days for review."
            )
            RowDivider()
            InfoRow(
                Icons.Outlined.Language, "Browser safety",
                "Off unless you turn it on. When on, adult-content detection in a browser " +
                        "runs entirely on your phone, matching visible text against a fixed list " +
                        "of terms. Nothing about your browsing is sent anywhere for this feature."
            )
            RowDivider()
            InfoRow(
                Icons.Outlined.Notifications, "Notifications",
                "CancelIt posts its own notifications for timers, check-ins and lock " +
                        "countdowns. It never reads other apps' notifications."
            )
            RowDivider()
            InfoRow(
                Icons.Outlined.Block, "What's logged",
                "Your activity log stores which app or topic triggered a block and when, " +
                        "never the specific text you typed or viewed."
            )
        }
    }
    item {
        Text(
            "By tapping \"I agree\", you agree to this and to the Terms of Use. You can " +
                    "change what's sent to Gemini at any time — Smart keyword detection is off " +
                    "by default and Study mode and the Coach are only used when you choose to use them.",
            style = StyleHint
        )
    }
    if (BuildConfig.PRIVACY_POLICY_URL.isNotBlank() || BuildConfig.TERMS_URL.isNotBlank()) {
        item {
            val context = LocalContext.current
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (BuildConfig.PRIVACY_POLICY_URL.isNotBlank()) {
                    SecondaryButton("Full privacy policy", Modifier.fillMaxWidth()) {
                        openUrl(context, BuildConfig.PRIVACY_POLICY_URL)
                    }
                }
                if (BuildConfig.TERMS_URL.isNotBlank()) {
                    SecondaryButton("Terms of Use", Modifier.fillMaxWidth()) { openUrl(context, BuildConfig.TERMS_URL) }
                }
            }
        }
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

/**
 * Asked once, after the privacy policy. Gemini's terms only allow users 18 and over, so the
 * answer decides whether any AI feature can run; everything else in CancelIt works either way.
 * "Delete all my data" clears the answer, which is how a mistaken tap is undone.
 */
@Composable
internal fun AgeQuestionScreen(onAnswer: (String) -> Unit) = FullScreenNotice(
    title = "Are you 18 or older?",
    subtitle = "One question before you start.",
    primaryLabel = "I'm 18 or older",
    onPrimary = { onAnswer(AGE_ADULT) },
    secondaryLabel = "I'm under 18",
    onSecondary = { onAnswer(AGE_MINOR) }
) {
    item {
        GroupCard {
            InfoRow(
                Icons.Outlined.AutoAwesome, "AI features are for adults",
                "The Coach, Smart keyword detection and Study mode's topic check use " +
                        "Google's Gemini, which is only for people 18 and over."
            )
            RowDivider()
            InfoRow(
                Icons.Outlined.Shield, "Everything else works at any age",
                "Time limits, app locks, blocked words, browser safety and check-ins " +
                        "run on your phone without AI."
            )
        }
    }
}

/**
 * Google Play's prominent disclosure for the Accessibility API: its own screen (not part of the
 * privacy policy), shown before the user is sent to system Settings, saying what is read, what
 * it's used for and what leaves the phone, and needing an explicit "Agree". Keep the wording in
 * step with what FocusAccessibilityService actually does.
 */
@Composable
internal fun AccessibilityDisclosureScreen(serviceAlreadyOn: Boolean, onAgree: () -> Unit, onDecline: () -> Unit) {
    BackHandler(onBack = onDecline)
    FullScreenNotice(
        title = "Accessibility permission",
        subtitle = "CancelIt uses Android's Accessibility service. Here's what it does with it.",
        primaryLabel = if (serviceAlreadyOn) "Agree" else "Agree and open Settings",
        onPrimary = onAgree,
        secondaryLabel = "Not now",
        onSecondary = onDecline
    ) {
        item {
            GroupCard {
                InfoRow(
                    Icons.Outlined.Shield, "What it reads",
                    "Which app is open, the text shown on screen, and the text you type in " +
                            "other apps. Password fields are always skipped."
                )
                RowDivider()
                InfoRow(
                    Icons.Outlined.Block, "What it's used for",
                    "Closing and locking apps when your time limits run out or you type a " +
                            "blocked word, checking you stay on topic in Study mode, and, if you " +
                            "turn on Browser safety, pausing a browser that shows explicit-content " +
                            "words. When AI only suspects a match, it asks you before closing anything."
                )
                RowDivider()
                InfoRow(
                    Icons.Outlined.AutoAwesome, "What leaves your phone",
                    "Nothing, unless you're 18+ and use these features: Smart keyword detection " +
                            "sends short text you type, and Study mode sends recent on-screen text, " +
                            "through Google's Firebase to Google's Gemini API to check the topic. " +
                            "CancelIt doesn't store, sell or use it for ads. Google may use it to " +
                            "improve its products."
                )
                RowDivider()
                InfoRow(
                    Icons.Outlined.Lock, "You stay in control",
                    "You can turn the service off at any time in Settings > Accessibility. " +
                            "CancelIt can't close or lock apps while it's off."
                )
            }
        }
        item {
            Text(
                if (serviceAlreadyOn) {
                    "The service is already switched on, but CancelIt won't use it until you agree."
                } else {
                    "Tapping \"Agree\" opens Settings, where you switch CancelIt on yourself."
                },
                style = StyleHint
            )
        }
    }
}
