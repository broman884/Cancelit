package com.cancelit.app.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cancelit.app.R
import com.cancelit.app.ui.FullScreenNotice
import com.cancelit.app.ui.GroupCard
import com.cancelit.app.ui.RowDivider
import com.cancelit.app.ui.SectionTitle
import com.cancelit.app.ui.StyleBodyStrong
import com.cancelit.app.ui.StyleCaption
import com.cancelit.app.ui.StyleHint

private const val APACHE_2 = "Apache License 2.0"
private const val BSD_3 = "BSD 3-Clause License (text below)"
private const val MIT = "MIT License (text below)"

/** The libraries in the release build, grouped by who makes them. Check it against
 *  `gradlew :app:dependencies --configuration releaseRuntimeClasspath` whenever a dependency is
 *  added, and add the text of any license other than Apache 2.0 to res/raw/other_licenses.txt. */
private val LIBRARIES = listOf(
    Triple(
        "AndroidX and Jetpack Compose",
        "Google. Activity, Core, Lifecycle, Compose UI, Foundation, Material 3, Material Icons, DataStore, Fragment, Emoji2, Startup and their helpers.",
        APACHE_2
    ),
    Triple("Kotlin standard library, kotlinx.coroutines, kotlinx.serialization and kotlinx-io", "JetBrains.", APACHE_2),
    Triple("Firebase AI Logic, Cloud Firestore and App Check", "Google.", APACHE_2),
    Triple("Ktor client", "JetBrains.", APACHE_2),
    Triple("gRPC", "The gRPC Authors.", APACHE_2),
    Triple("OkHttp and Okio", "Square.", APACHE_2),
    Triple(
        "Guava, Gson, Error Prone annotations, J2ObjC annotations, JSR 305, JSpecify, PerfMark, javax.inject, JetBrains annotations",
        "Google, JetBrains, the JSpecify and JSR-330 groups, and others.",
        APACHE_2
    ),
    Triple("Protocol Buffers (Java Lite)", "Google.", BSD_3),
    Triple("RE2/J", "Google and The Go Authors.", BSD_3),
    Triple("SLF4J", "QOS.ch.", MIT),
    Triple("Animal Sniffer annotations", "MojoHaus (codehaus.org).", MIT),
    Triple("Reactive Streams", "Reactive Streams contributors.", "MIT No Attribution License"),
    Triple("Google Play services (Base, Basement, Tasks)", "Google.", "Android Software Development Kit License"),
    Triple("Play Integrity and Play Core common", "Google.", "Play Integrity API Terms of Service; Play Core Software Development Kit Terms of Service")
)

/** The open-source libraries CancelIt is built with, and the license texts they ask apps to
 *  pass on to their users: the BSD and MIT ones, and the Apache License 2.0 most of them use. */
@Composable
internal fun LicensesScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val apacheText = remember { readRaw(context, R.raw.apache_license_2_0) }
    val otherText = remember { readRaw(context, R.raw.other_licenses) }
    FullScreenNotice(
        title = "Open-source licenses",
        subtitle = "CancelIt is built with these libraries.",
        primaryLabel = "Close",
        onPrimary = onClose
    ) {
        item {
            GroupCard {
                LIBRARIES.forEachIndexed { index, (name, by, license) ->
                    if (index > 0) RowDivider()
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(name, style = StyleBodyStrong)
                        Spacer(Modifier.height(2.dp))
                        Text(by, style = StyleCaption)
                        Spacer(Modifier.height(2.dp))
                        Text(license, style = StyleHint)
                    }
                }
            }
        }
        if (otherText.isNotBlank()) {
            item { SectionTitle("BSD and MIT licenses") }
            item { Text(otherText, style = StyleHint) }
        }
        if (apacheText.isNotBlank()) {
            item { SectionTitle(APACHE_2) }
            item { Text(apacheText, style = StyleHint) }
        }
    }
}

private fun readRaw(context: Context, id: Int): String =
    runCatching { context.resources.openRawResource(id).bufferedReader().use { it.readText() } }.getOrDefault("")
