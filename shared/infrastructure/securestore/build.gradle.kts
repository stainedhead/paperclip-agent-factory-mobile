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
}
