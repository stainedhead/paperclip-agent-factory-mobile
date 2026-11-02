import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    id("pafm.kmp-library")
    alias(libs.plugins.skie)
}

kotlin {
    val xcframework = XCFramework("PaperclipKit")
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "PaperclipKit"
            binaryOption("bundleId", "com.stainedhead.paperclipmobile.kit")
            // The iOS app imports only this framework, so everything it renders must be exported through it.
            export(project(":shared:presentation"))
            export(project(":shared:application"))
            export(project(":shared:domain"))
            export(project(":shared:infrastructure:platform"))
            xcframework.add(this)
        }
    }

    // Architecture rules (Konsist) run on the JVM from their own compilation so they stay out of the product build.
    jvm {
        val konsist = compilations.create("konsist")
        konsist.defaultSourceSet {
            kotlin.srcDir("src/konsistTest/kotlin")
            dependencies {
                implementation(libs.konsist)
                implementation(libs.junit.jupiter)
                runtimeOnly(libs.junit.platform.launcher)
            }
        }
        project.tasks.register<Test>("konsistTest") {
            group = "verification"
            description = "Runs the Clean Architecture rules (Konsist)."
            testClassesDirs = konsist.output.classesDirs
            classpath = konsist.runtimeDependencyFiles + konsist.output.allOutputs
            systemProperty("pafm.rootDir", rootProject.projectDir.absolutePath)
            // The rules read the shared modules' sources, so those sources are inputs: editing them re-runs the rules.
            inputs
                .files(
                    rootProject.fileTree("shared") {
                        include("**/src/**/*.kt")
                        exclude("**/build/**")
                    },
                ).withPropertyName("scannedSources")
                .withPathSensitivity(PathSensitivity.RELATIVE)
            useJUnitPlatform()
        }
    }

    sourceSets.commonMain.dependencies {
        api(project(":shared:presentation"))
        api(project(":shared:application"))
        api(project(":shared:domain"))
        api(project(":shared:infrastructure:platform"))
        implementation(project(":shared:testing"))
        implementation(libs.koin.core)
    }
}
