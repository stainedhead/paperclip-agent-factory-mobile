plugins {
    id("pafm.android-app")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

dependencies {
    // The only shared module the app imports (AGENTS.md "Swift and Kotlin Interop" has the same rule for iOS).
    implementation(project(":shared:umbrella"))
    implementation(libs.coroutines.core)
    implementation(libs.serialization.json)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    // FragmentActivity (BiometricPrompt needs one) comes with the biometric library; Custom Tabs for pairing.
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.runtime)
    // The UI libraries must be app dependencies (not test-only) so their R classes reach the Robolectric classpath.
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    // Compose already pulls it in at run time; declared so the manifest can name (and remove) its exported receiver.
    implementation(libs.androidx.profileinstaller)

    // Unit tests: JUnit 4 + Robolectric for Compose and Roborazzi; JUnit 5 for pure tests through the Vintage engine.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.junit4)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    // Hosts the empty Activity the Compose rule launches under Robolectric.
    debugImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

android {
    namespace = "com.stainedhead.paperclipmobile.android"
    buildFeatures {
        // BuildConfig.DEBUG decides whether launch extras may steer the app onto fakes (demo mode, debug builds only).
        buildConfig = true
    }
    defaultConfig {
        applicationId = "com.stainedhead.paperclipmobile"
        versionCode = 1
        versionName = "0.1.0"
    }
    lint {
        // Zero new warnings is the gate (AGENTS.md); user-facing text lives in string resources.
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = true
        error += "HardcodedText"
        lintConfig = file("lint.xml")
    }
    testOptions {
        unitTests {
            // Robolectric needs the merged resources and manifest.
            isIncludeAndroidResources = true
            all {
                it.useJUnitPlatform()
                // Robolectric reaches into JDK internals (FileDescriptor, SharedSecrets) that JDK 17+ encapsulates.
                it.jvmArgs(
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                )
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}
