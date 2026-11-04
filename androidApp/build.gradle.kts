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
    implementation(libs.androidx.core.splashscreen)
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
        // The commit count: monotonic on one branch and needs no file edit per build. CI must fetch full history.
        versionCode =
            providers
                .exec {
                    commandLine("git", "rev-list", "--count", "HEAD")
                    isIgnoreExitValue = true
                }.standardOutput.asText
                .map { it.trim().toIntOrNull() ?: 1 }
                .getOrElse(1)
        versionName =
            libs.versions.android.app
                .get()
        // Only the releaseDemo build type turns the demo entry on (debug has it through BuildConfig.DEBUG).
        buildConfigField("boolean", "DEMO_ENTRY", "false")
    }
    signingConfigs {
        // The upload key is a CI secret or a local file, never committed (.gitignore excludes *.jks and *.keystore).
        // Without it the release build is signed with the debug key so it installs on a test device; such an APK is
        // not a distribution artifact (ADR-132).
        create("upload") {
            val store = providers.environmentVariable("PAFM_UPLOAD_STORE_FILE").orNull
            if (store != null) {
                storeFile = file(store)
                storePassword = providers.environmentVariable("PAFM_UPLOAD_STORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("PAFM_UPLOAD_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("PAFM_UPLOAD_KEY_PASSWORD").orNull
            }
        }
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
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val upload = signingConfigs.getByName("upload")
            signingConfig = if (upload.storeFile != null) upload else signingConfigs.getByName("debug")
        }
        // The shipping build plus the demo entry, so the R8 output can be launched without a server (ADR-132). It has
        // its own application id, is signed with the debug key and is never distributed.
        create("releaseDemo") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            applicationIdSuffix = ".demo"
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField("boolean", "DEMO_ENTRY", "true")
        }
        // The two on-demand benchmark builds (T8.6, ADR-135), driven by :androidApp:benchmark and never distributed.
        // `benchmark` is releaseDemo (R8 on, demo entry so there is an Inbox without a server) with its own application
        // id and a profileable, profile-installable manifest (src/benchmark). `nonMinified` is the same without R8, so
        // the baseline profile generator records real class names. Neither is debuggable.
        create("benchmark") {
            initWith(getByName("releaseDemo"))
            matchingFallbacks += "release"
            applicationIdSuffix = ".benchmark"
        }
        create("nonMinified") {
            initWith(getByName("benchmark"))
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
    sourceSets.getByName("nonMinified").manifest.srcFile("src/benchmark/AndroidManifest.xml")
}
