pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Settings plugins cannot read the version catalog; this is the one literal version in the build.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "paperclip-agent-factory-mobile"

include(
    ":androidApp",
    ":shared:domain",
    ":shared:application",
    ":shared:presentation",
    ":shared:testing",
    ":shared:umbrella",
    ":shared:infrastructure:api",
    ":shared:infrastructure:realtime",
    ":shared:infrastructure:persistence",
    ":shared:infrastructure:securestore",
    ":shared:infrastructure:platform",
)
