import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlinx.kover")
}

val libs = the<VersionCatalogsExtension>().named("libs")
val namespaceRoot = "com.stainedhead.paperclipmobile"

// Gradle path ":shared:infrastructure:api" -> "com.stainedhead.paperclipmobile.shared.infrastructure.api"
val moduleNamespace = namespaceRoot + project.path.replace(':', '.').replace('-', '_')

extensions.configure<KotlinMultiplatformExtension> {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }

    jvm {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
    }
    iosArm64()
    iosSimulatorArm64()
    extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
        namespace = moduleNamespace
        compileSdk = 37
        minSdk = 28
        withHostTest { }
    }

    sourceSets.commonTest.dependencies {
        implementation(kotlin("test"))
        implementation(libs.findLibrary("kotest-assertions").get())
        implementation(libs.findLibrary("turbine").get())
        implementation(libs.findLibrary("coroutines-test").get())
    }
}
