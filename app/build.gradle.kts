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
        versionCode = 1
        versionName = "1.0.0"
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
        // The app is Polish only for now (Ukrainian is planned for 1.3).
        // Drops unused library translations from the APK.
        localeFilters += listOf("pl")
    }

    packaging {
        resources {
            excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/*.version")
        }
    }

    lint {
        abortOnError = true
        checkDependencies = false
        // Strings are Polish in the default values/ folder. targetSdk 36 is
        // deliberate (Play requirement 2026); version checks need the network.
        disable += listOf("MissingTranslation", "OldTargetApi", "NewerVersionAvailable")
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
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
