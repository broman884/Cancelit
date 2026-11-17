import java.util.Properties
import java.io.FileInputStream

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        load(FileInputStream(localFile))
    }
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.cancelit.app"
    compileSdk = 36
    defaultConfig {
        // The app's identity on Google Play and in Firebase (permanent once published). The code
        // itself stays in the com.cancelit.app namespace above; the two don't have to match.
        applicationId = "com.gndstudious.cancelit"
        minSdk = 26
        // Google Play has required API 36 (Android 16) for new apps and updates since August 31, 2026.
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        // AI goes through Firebase AI Logic, which holds the Gemini key on Google's side and only
        // answers requests carrying a Firebase App Check token. No key is ever put in the app,
        // not even in debug builds, because anything in an APK can be extracted.
        // Where in-app "Report" sends flagged AI replies (Google Play's AI-generated content policy).
        buildConfigField("String", "SUPPORT_EMAIL", "\"${localProperties.getProperty("SUPPORT_EMAIL", "")}\"")
        // Public pages for the hosted privacy policy and terms (drafts in legal/).
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"${localProperties.getProperty("PRIVACY_POLICY_URL", "")}\"")
        buildConfigField("String", "TERMS_URL", "\"${localProperties.getProperty("TERMS_URL", "")}\"")
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        create("release") {
            val storeFilePath = localProperties.getProperty("RELEASE_STORE_FILE", "")
            if (storeFilePath.isNotBlank()) {
                storeFile = file(storeFilePath)
                storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD", "")
                keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS", "")
                keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD", "")
            }
        }
    }

    buildTypes {
        release {
            // R8 drops unused code (most of material-icons-extended) and unused resources.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

        }

    }
}

// Release checklist: warn (don't fail) so a local release build still works while setting up.
gradle.taskGraph.whenReady {
    if (localProperties.getProperty("GEMINI_API_KEY", "").isNotBlank()) {
        logger.warn("CancelIt: GEMINI_API_KEY in local.properties isn't used by the app. Firebase AI Logic manages the key; delete it from local.properties.")
    }
    if (allTasks.any { it.name.contains("Release") }) {
        if (!file("google-services.json").exists()) {
            logger.warn("CancelIt: app/google-services.json is missing, so AI features are off in this release build.")
        }
        if (localProperties.getProperty("SUPPORT_EMAIL", "").isBlank()) {
            logger.warn("CancelIt: SUPPORT_EMAIL is not set; Play requires a working report option for AI replies.")
        }
        if (localProperties.getProperty("PRIVACY_POLICY_URL", "").isBlank()) {
            logger.warn("CancelIt: PRIVACY_POLICY_URL is not set; Play requires a privacy policy link inside the app.")
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    // Google Play services (via Firebase) pulls in Fragment 1.1.0, and registerForActivityResult
    // (the notification permission request in MainActivity) needs 1.3.0 or newer. Without this
    // line lint stops every release build with InvalidFragmentVersionForActivityResult.
    implementation("androidx.fragment:fragment:1.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Firebase AI Logic calls Gemini without a key in the app. Firebase App Check proves each
    // request comes from the genuine app (Play Integrity in release, a registered debug token in
    // debug builds). Setup steps: FIREBASE_SETUP.md.
    implementation(platform("com.google.firebase:firebase-bom:35.1.0"))
    implementation("com.google.firebase:firebase-ai")
    // Cloud Firestore keeps reported Coach replies (add-only; see FIREBASE_SETUP.md for the rules).
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")

    testImplementation("junit:junit:4.13.2")
    // The real org.json for unit tests: Android's copy only works on a device.
    testImplementation("org.json:json:20240303")
}