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
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}
