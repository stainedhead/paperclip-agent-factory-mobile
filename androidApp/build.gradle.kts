plugins {
    id("pafm.android-app")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
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
}

android {
    namespace = "com.stainedhead.paperclipmobile.android"
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
