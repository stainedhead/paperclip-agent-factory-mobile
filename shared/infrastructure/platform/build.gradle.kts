plugins {
    id("pafm.kmp-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:domain"))
        }
        androidMain.dependencies {
            // FragmentActivity (AuthHost) is part of the public surface, hence api.
            api(libs.androidx.biometric)
        }
        commonTest.dependencies {
            implementation(project(":shared:testing"))
        }
    }
}
