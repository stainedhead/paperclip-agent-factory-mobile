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
        compileSdk = PafmAndroid.COMPILE_SDK
        minSdk = PafmAndroid.MIN_SDK
        withHostTest { }
        // Instrumented tests run only on an emulator or device (connectedAndroidDeviceTest); `check` never touches
        // them. A module opts in by having an src/androidDeviceTest directory.
        if (layout.projectDirectory
                .dir("src/androidDeviceTest")
                .asFile.isDirectory
        ) {
            withDeviceTest {
                instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
        }
    }
    sourceSets.matching { it.name == "androidDeviceTest" }.configureEach {
        dependencies {
            implementation(kotlin("test"))
            implementation(libs.findLibrary("androidx-test-runner").get())
            implementation(libs.findLibrary("androidx-test-core").get())
            implementation(libs.findLibrary("androidx-test-ext-junit").get())
            implementation(libs.findLibrary("coroutines-test").get())
        }
    }

    sourceSets.commonTest.dependencies {
        implementation(kotlin("test"))
        implementation(libs.findLibrary("kotest-assertions").get())
        implementation(libs.findLibrary("turbine").get())
        implementation(libs.findLibrary("coroutines-test").get())
    }
}
