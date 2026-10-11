plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.benekapps.myslnik"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.benekapps.myslnik"
        minSdk = 26
        // Google Play requires API 36 for new versions from 31.08.2026.
        targetSdk = 36
        versionCode = 2
        versionName = "1.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing for GitHub Actions (.github/workflows/android.yml).
    // Without these variables the release build is unsigned and
    // "Generate Signed Bundle" in Android Studio works as usual.
    val ciKeystore = System.getenv("ANDROID_KEYSTORE_PATH")
    if (ciKeystore != null) {
        signingConfigs {
            create("ci") {
                storeFile = file(ciKeystore)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (ciKeystore != null) {
                signingConfig = signingConfigs.getByName("ci")
            }
            // R8: shrinks, optimizes and removes unused resources.
            // Keep rules: app/src/main/keepRules/rules.keep
            optimization {
                enable = true
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        // Polish (default) and Ukrainian. Drops unused library translations
        // and lists both languages in Android's per-app language settings.
        localeFilters += listOf("pl", "uk")
        generateLocaleConfig = true
    }

    bundle {
        language {
            // The in-app language switch needs both languages on every device;
            // Play would otherwise install only the phone's language.
            enableSplit = false
        }
    }

    packaging {
        resources {
            excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/*.version")
        }
    }

    lint {
        abortOnError = true
        checkDependencies = false
        // targetSdk 36 is deliberate (Play requirement 2026); version checks
        // need the network. MissingTranslation stays on: Ukrainian must be complete.
        disable += listOf("OldTargetApi", "NewerVersionAvailable")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

room {
    // Exported schemas are committed and used by the migration tests.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.biometric)
    // biometric 1.1.0 pulls in an old androidx.fragment; use a current one.
    implementation(libs.androidx.fragment.ktx)
    // In-app language switch (Polish / Ukrainian) on every Android version.
    implementation(libs.androidx.appcompat)
    // Reminders without exact alarms.
    implementation(libs.androidx.work.runtime.ktx)
    // Voluntary "Wesprzyj projekt" tips. Talks to the Play Store app over IPC,
    // so no INTERNET permission is needed (the manifest removes it anyway).
    implementation(libs.android.billing.ktx) {
        // Billing's own telemetry (Google datatransport) would upload diagnostics.
        // The app sends nothing anywhere; billing guards these calls with
        // try/catch and works without them (checked in SupportBillingTest).
        exclude(group = "com.google.android.datatransport")
    }
    constraints {
        // room-testing (migration tests) needs kotlinx-serialization 1.8+.
        // Test dependencies are aligned to the app's, so raise it here.
        implementation(libs.kotlinx.serialization.core)
    }

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // org.json is part of Android, but not of the JVM used by unit tests.
    testImplementation(libs.org.json)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.espresso.intents)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
