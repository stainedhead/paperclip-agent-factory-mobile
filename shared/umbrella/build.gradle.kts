import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    id("pafm.kmp-library")
    alias(libs.plugins.skie)
}

// The pinned-server stand-in the API adapter tests use, shared (copied, not moved) so the live-graph tests can drive
// real flows through a Ktor MockEngine. Only the stub and its fixtures, never the adapter tests (ADR-067).
val sharedStubSources =
    tasks.register<Sync>("sharedStubSources") {
        from(project(":shared:infrastructure:api").layout.projectDirectory.dir("src/commonTest/kotlin")) {
            include("**/Stub*.kt", "**/*Fixtures.kt")
        }
        into(layout.buildDirectory.dir("generated/sharedStub/kotlin"))
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
        // SQLDelight links sqlite only into the module that applies its plugin; the framework and its tests use it too.
        binaries.all { linkerOpts("-lsqlite3") }
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
            // The manifest rules read the app's merged manifests, so the manifest merge runs first (device-free).
            listOf("debug", "release", "releaseDemo").forEach { variant ->
                val name = variant.replaceFirstChar { it.uppercase() }
                dependsOn(":androidApp:process${name}Manifest")
                systemProperty(
                    "pafm.mergedManifest.$variant",
                    rootProject.layout.projectDirectory
                        .file(
                            "androidApp/build/intermediates/merged_manifests/$variant/process${name}Manifest/AndroidManifest.xml",
                        ).asFile.absolutePath,
                )
            }
            // The rules read the shared modules' sources, so those sources are inputs: editing them re-runs the rules.
            inputs
                .files(
                    rootProject.fileTree("shared") {
                        include("**/src/**/*.kt")
                        exclude("**/build/**")
                    },
                ).withPropertyName("scannedSources")
                .withPathSensitivity(PathSensitivity.RELATIVE)
            // The app rules read the Compose app's sources and resources, so editing them re-runs the rules too.
            inputs
                .files(
                    rootProject.fileTree("androidApp/src") {
                        include("**/*.kt", "**/*.xml")
                    },
                ).withPropertyName("scannedAppSources")
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
        // The live graph (AD-040): the real adapters behind the ports.
        implementation(project(":shared:infrastructure:api"))
        implementation(project(":shared:infrastructure:persistence"))
        implementation(project(":shared:infrastructure:securestore"))
        implementation(project(":shared:infrastructure:platform"))
    }
    sourceSets.getByName("androidHostTest").dependencies {
        implementation(libs.ktor.client.mock)
    }
    sourceSets.jvmTest {
        kotlin.srcDir(sharedStubSources)
        dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.serialization.json)
        }
    }
}
