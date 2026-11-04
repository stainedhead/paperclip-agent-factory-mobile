plugins {
    `kotlin-dsl`
}

// Convention plugins compile against the plugin artifacts they configure; versions come from the catalog.
fun Provider<PluginDependency>.asDependency() = map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }

dependencies {
    implementation(
        libs.plugins.kotlin.multiplatform
            .asDependency(),
    )
    implementation(
        libs.plugins.android.kmp.library
            .asDependency(),
    )
    implementation(
        libs.plugins.android.application
            .asDependency(),
    )
    implementation(libs.plugins.kover.asDependency())
}

kotlin {
    jvmToolchain(21)
}
