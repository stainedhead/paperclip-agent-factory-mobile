plugins {
    id("pafm.kmp-library")
}

kotlin {
    sourceSets.commonTest.dependencies {
        implementation(project(":shared:testing"))
    }
    sourceSets.commonMain.dependencies {
        api(project(":shared:domain"))
    }
}
