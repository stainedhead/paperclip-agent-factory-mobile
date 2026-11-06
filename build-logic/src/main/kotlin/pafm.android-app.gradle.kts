import com.android.build.api.dsl.ApplicationExtension

plugins {
    id("com.android.application")
}

// Android application defaults shared with the Android library convention (PafmAndroid). The module sets its own
// namespace, application id and version.
extensions.configure<ApplicationExtension> {
    compileSdk = PafmAndroid.COMPILE_SDK
    defaultConfig {
        minSdk = PafmAndroid.MIN_SDK
        targetSdk = PafmAndroid.COMPILE_SDK
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
