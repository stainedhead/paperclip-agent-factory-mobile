plugins {
    id("pafm.android-app")
    alias(libs.plugins.kotlin.compose)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)
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
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}
