package com.cancelit.app.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cancelit.app.data.FocusStore
import com.cancelit.app.service.FocusAccessibilityService
import com.cancelit.app.ui.screens.AccessibilityDisclosureScreen
import com.cancelit.app.ui.screens.AgeQuestionScreen
import com.cancelit.app.ui.screens.BlacklistScreen
import com.cancelit.app.ui.screens.ChildModeScreen
import com.cancelit.app.ui.screens.CoachScreen
import com.cancelit.app.ui.screens.FocusScreen
import com.cancelit.app.ui.screens.LicensesScreen
import com.cancelit.app.ui.screens.PrivacyPolicyScreen
import com.cancelit.app.ui.screens.ReportScreen
import com.cancelit.app.ui.screens.StudyScreen

/** Shows the animated CancelIt splash first, then the privacy policy and the age question on
 *  first launch, then hands off to the real app. Every route to Accessibility settings passes
 *  the separate Accessibility disclosure first. */
@Composable
internal fun AppRoot(store: FocusStore, openAccessibility: () -> Unit, context: Context) {
    var showSplash by remember { mutableStateOf(true) }
    var privacyAccepted by remember { mutableStateOf(store.privacyAccepted()) }
    var ageGroup by remember { mutableStateOf(store.ageGroup()) }
    var a11yConsent by remember { mutableStateOf(store.accessibilityConsent()) }
    var showA11yDisclosure by remember { mutableStateOf(false) }

    // Switched on before agreeing (straight from system Settings): the service stays idle until
    // the user has seen and accepted the disclosure.
    LaunchedEffect(privacyAccepted) {
        if (privacyAccepted && !a11yConsent && FocusAccessibilityService.isEnabled(context)) showA11yDisclosure = true
    }

    CancelItTheme {
        when {
            showSplash -> AnimatedSplashScreen(onFinished = { showSplash = false })
            !privacyAccepted -> PrivacyPolicyScreen(
                readOnly = false,
                onAccept = { store.setPrivacyAccepted(true); privacyAccepted = true },
                onDecline = { (context as? ComponentActivity)?.finish() }
            )
            ageGroup == null -> AgeQuestionScreen { group -> store.setAgeGroup(group); ageGroup = group }
            showA11yDisclosure -> AccessibilityDisclosureScreen(
                serviceAlreadyOn = FocusAccessibilityService.isEnabled(context),
                onAgree = {
                    store.setAccessibilityConsent(true)
                    a11yConsent = true
                    showA11yDisclosure = false
                    if (!FocusAccessibilityService.isEnabled(context)) openAccessibility()
                },
                onDecline = { showA11yDisclosure = false }
            )
            else -> CancelItApp(
                store,
                openAccessibility = {
                    if (a11yConsent) {
                        openAccessibility()
                    } else {
                        showA11yDisclosure = true
                    }
                },
                context = context
            )
        }
    }
}

private enum class Tab { FOCUS, BLACKLIST, STUDY, REPORT, COACH, CHILD }

private data class NavItem(val tab: Tab, val icon: ImageVector, val label: String)

private val NavItems = listOf(
    NavItem(Tab.FOCUS, Icons.Outlined.Shield, "Focus"),
    NavItem(Tab.BLACKLIST, Icons.Outlined.Block, "List"),
    NavItem(Tab.STUDY, Icons.Outlined.School, "Study"),
    NavItem(Tab.REPORT, Icons.Outlined.BarChart, "Report"),
    NavItem(Tab.COACH, Icons.Outlined.AutoAwesome, "Coach"),
    NavItem(Tab.CHILD, Icons.Outlined.Lock, "PIN")
)

@Composable
private fun CancelItApp(store: FocusStore, openAccessibility: () -> Unit, context: Context) {
    var currentTab by remember { mutableStateOf(Tab.FOCUS) }
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }

    // If a presence prompt is waiting when the app is opened, jump straight to the Study tab so
    // the big "I'm here" button is the first thing on screen.
    LaunchedEffect(Unit) { if (store.presencePending()) currentTab = Tab.STUDY }

    if (showPrivacyPolicy) {
        PrivacyPolicyScreen(
            readOnly = true, onAccept = {}, onDecline = {},
            onClose = { showPrivacyPolicy = false }
        )
    } else if (showLicenses) {
        LicensesScreen(onClose = { showLicenses = false })
    } else {
        Surface(Modifier.fillMaxSize(), color = Ink) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
                Box(Modifier.weight(1f)) {
                    Crossfade(targetState = currentTab, animationSpec = tween(160), label = "tab") { tab ->
                        when (tab) {
                            Tab.FOCUS -> FocusScreen(
                                store, openAccessibility, context,
                                goToChild = { currentTab = Tab.CHILD },
                                goToBlacklist = { currentTab = Tab.BLACKLIST },
                                openPrivacyPolicy = { showPrivacyPolicy = true },
                                openLicenses = { showLicenses = true }
                            )
                            Tab.BLACKLIST -> BlacklistScreen(store)
                            Tab.STUDY -> StudyScreen(store)
                            Tab.REPORT -> ReportScreen(store)
                            Tab.COACH -> CoachScreen(store)
                            Tab.CHILD -> ChildModeScreen(store)
                        }
                    }
                }
                BottomBar(currentTab) { currentTab = it }
            }
        }
    }
}

/** Six tabs is a lot for one row, so there's no stretched indicator: just a snug pill behind the icon. */
@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Ink).windowInsetsPadding(WindowInsets.navigationBars)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Border))
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
            NavItems.forEach { item ->
                val selected = item.tab == current
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSelect(item.tab) }
                        .padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .width(48.dp).height(30.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(if (selected) TextPrimary else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(item.icon, null, tint = if (selected) Ink else TextTertiary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.label, fontSize = 11.sp, maxLines = 1,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) TextPrimary else TextTertiary
                    )
                }
            }
        }
    }
}
