plugins {
    id("pafm.android-test")
}

// On-demand performance guard (T8.6, ADR-135). A separate module so that nothing here is part of `check`'s device work;
// `check` only compiles it. Run it with scripts/run-benchmarks.sh.
android {
    namespace = "com.stainedhead.paperclipmobile.android.benchmark"
    targetProjectPath = ":androidApp"
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        // Both build types exist in the app (androidApp/build.gradle.kts): `benchmark` is the R8-minified build that
        // is timed; `nonMinified` keeps real class names so the baseline profile generator records usable rules.
        create("benchmark") {
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
        create("nonMinified") {
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
    // The test APK instruments the app under test from its own process (a Macrobenchmark requirement).
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

// Only the two build types above are built; debug and release of this module have no app counterpart to measure.
androidComponents {
    beforeVariants(selector().all()) { it.enable = it.buildType == "benchmark" || it.buildType == "nonMinified" }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
}
