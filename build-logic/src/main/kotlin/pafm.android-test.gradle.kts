import com.android.build.api.dsl.TestExtension

plugins {
    id("com.android.test")
}

// Android instrumentation-test module defaults (a `com.android.test` module that drives another module's app). The
// module sets its own namespace and target project.
extensions.configure<TestExtension> {
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
