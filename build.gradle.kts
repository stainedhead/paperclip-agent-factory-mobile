import kotlinx.kover.gradle.plugin.dsl.AggregationType
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

plugins {
    // Declared once at the root so sibling modules share one plugin classloader (required for Kotlin/Native services).
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.skie) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

// ---------------------------------------------------------------- formatting (ktlint via Spotless)
spotless {
    // Build directories are pruned while the tree is walked (not filtered afterwards): a parallel compile task that
    // deletes a class file mid-walk otherwise makes spotless fail with "Could not read path".
    kotlin {
        target(
            fileTree(rootDir) {
                include("**/*.kt")
                exclude("**/build/**", "**/.gradle/**")
            },
        )
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target(
            fileTree(rootDir) {
                include("**/*.kts")
                exclude("**/build/**", "**/.gradle/**")
            },
        )
        ktlint(libs.versions.ktlint.get())
    }
}

// ---------------------------------------------------------------- static analysis
detekt {
    buildUponDefaultConfig = true
    config.setFrom("$rootDir/config/detekt/detekt.yml")
    parallel = true
    // One root task scans every module's Kotlin sources (common, platform and test source sets).
    source.setFrom(
        fileTree(rootDir) {
            include("shared/**/src/**/*.kt")
            exclude("**/build/**")
        },
    )
}

// ---------------------------------------------------------------- coverage (JVM runs of the shared tests)
// Two gates, each its own Kover variant so the numbers are never mixed:
//   "core"         = one aggregate over the domain and application packages (PRD NFR, AGENTS.md TDD section)
//   "presentation" = the presentation package on its own
// shared/testing, infrastructure, umbrella and the apps are measured by neither gate.
dependencies {
    kover(project(":shared:domain"))
    kover(project(":shared:application"))
    kover(project(":shared:presentation"))
}

val appPackage = "com.stainedhead.paperclipmobile"
val coverageGates =
    mapOf(
        "core" to listOf("$appPackage.domain", "$appPackage.application"),
        "presentation" to listOf("$appPackage.presentation"),
    )

kover {
    currentProject {
        // The root has no code of its own; it only declares the variants it aggregates.
        coverageGates.keys.forEach { createVariant(it) {} }
    }
    reports {
        coverageGates.forEach { (name, packages) ->
            variant(name) {
                filters {
                    includes {
                        packages(*packages.toTypedArray())
                    }
                }
                verify {
                    rule("$name line coverage at least 90%") {
                        bound {
                            minValue = 90
                            coverageUnits = CoverageUnit.LINE
                            aggregationForGroup = AggregationType.COVERED_PERCENTAGE
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- one entry point
tasks.named("check") {
    dependsOn("koverVerifyCore", "koverVerifyPresentation", ":shared:umbrella:konsistTest")
}
