plugins {
    id("pafm.kmp-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:domain"))
        }
        commonTest.dependencies {
            implementation(project(":shared:testing"))
        }
    }
    // The emulator tests run the shared contract suites against the real adapters.
    sourceSets.matching { it.name == "androidDeviceTest" }.configureEach {
        dependencies {
            implementation(project(":shared:testing"))
        }
    }
}
