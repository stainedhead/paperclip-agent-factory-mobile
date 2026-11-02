plugins {
    id("pafm.kmp-library")
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(libs.coroutines.core)
        api(libs.datetime)
    }
}

kover {
    currentProject {
        // Both variants exist in every measured module; the root report for each filters by package.
        createVariant("core") { add("jvm") }
        createVariant("presentation") { add("jvm") }
    }
}
