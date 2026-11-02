plugins {
    id("pafm.kmp-library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:domain"))
            implementation(libs.ktor.client.core)
            implementation(libs.serialization.json)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        commonTest.dependencies {
            implementation(project(":shared:testing"))
            implementation(libs.ktor.client.mock)
        }
    }

    // The contract test (AD-050) runs the production adapters, with a real engine, against a live pinned Paperclip
    // server. It lives in its own JVM compilation so it never runs in `check` or `allTests` (it needs a server);
    // `./gradlew :shared:infrastructure:api:contractTest` runs it, `check` only compiles it.
    jvm {
        val main = compilations.getByName("main")
        val contract = compilations.create("contract") { associateWith(main) }
        contract.defaultSourceSet {
            kotlin.srcDir("src/contractTest/kotlin")
            resources.srcDir("src/contractTest/resources")
            dependencies {
                implementation(project(":shared:testing"))
                implementation(libs.serialization.json)
                implementation(libs.coroutines.core)
                implementation(libs.junit.jupiter)
                implementation(libs.junit.platform.launcher)
            }
        }
        project.tasks.register<Test>("contractTest") {
            group = "verification"
            description = "Runs the real adapters against a live Paperclip server (PAPERCLIP_CONTRACT_BASE_URL)."
            testClassesDirs = contract.output.classesDirs
            classpath = contract.runtimeDependencyFiles + contract.output.allOutputs
            // A server run is never up to date: the same sources meet a new container.
            outputs.upToDateWhen { false }
            listOf("PAPERCLIP_CONTRACT_BASE_URL", "PAPERCLIP_CONTRACT_BOOTSTRAP_INVITE", "PAPERCLIP_CONTRACT_REQUIRED")
                .forEach { name -> providers.environmentVariable(name).orNull?.let { environment(name, it) } }
            systemProperty(
                "pafm.reportDir",
                layout.buildDirectory
                    .dir("reports/contract")
                    .get()
                    .asFile.absolutePath,
            )
            testLogging {
                showStandardStreams = true
                events("failed", "skipped")
                showExceptions = true
                showCauses = true
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            }
            useJUnitPlatform()
        }
    }
}

// `check` proves the contract sources compile; it does not need (or start) a server.
tasks.named("check") { dependsOn("compileContractKotlinJvm") }
