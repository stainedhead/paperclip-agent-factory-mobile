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

kover {
    currentProject {
        // Both variants exist in every measured module; the root report for each filters by package.
        createVariant("core") { add("jvm") }
        createVariant("presentation") { add("jvm") }
    }
}
