package com.stainedhead.paperclipmobile.architecture

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File

private val TESTING_PACKAGE = "$APP_PACKAGE.testing"

/** The module functions that bind fakes; the live graph must never call them (ADR-068). */
private val FAKE_MODULES = listOf("portsModule(", "demoPortsModule(", "sliceThreePorts(", "signalPorts(")

internal fun liveWiringSources(umbrellaSrc: File): List<File> =
    umbrellaSrc
        .walkTopDown()
        .filter { it.extension == "kt" && it.path.contains("Main/") }
        .filter { it.name.startsWith("Live") }
        .toList()

internal fun fakeUses(source: String): List<String> {
    val code = source.lines().filterNot { it.trim().startsWith("*") || it.trim().startsWith("//") }
    val imports =
        code.map { it.trim() }.filter { it.startsWith("import $TESTING_PACKAGE") }
    val calls = FAKE_MODULES.filter { call -> code.any { line -> line.contains(call) } }
    return imports + calls
}

/**
 * The live graph (Phase 1b AD-040): the files that wire real adapters (`Live*.kt`, in every source set) may not
 * import the testing module nor call a module function that binds fakes. The runtime twin of this rule is
 * `LiveGraphTest`, which resolves the ports and checks no fake is behind them. `PlatformServices.kt` holds the iOS
 * Keychain test hook and is deliberately not one of the guarded files.
 */
class LiveWiringRulesTest {
    private val umbrellaSrc = File(projectRoot, "shared/umbrella/src")

    @Test
    fun `the live wiring files import nothing from the testing module and bind no fakes`() {
        val files = liveWiringSources(umbrellaSrc)
        Assertions.assertTrue(files.isNotEmpty(), "No live wiring files found: the rule would pass vacuously")
        val offenders = files.associate { it.name to fakeUses(it.readText()) }.filterValues { it.isNotEmpty() }
        Assertions.assertTrue(offenders.isEmpty(), "Fakes in the live wiring: $offenders")
    }

    @Test
    fun `the rule selects the live graph files it is meant to guard`() {
        val names = liveWiringSources(umbrellaSrc).map { it.name }
        listOf("LiveGraph.kt", "LiveModules.kt", "LivePlatform.kt").forEach {
            Assertions.assertTrue(it in names, "$it is not guarded; selected: $names")
        }
    }

    @Test
    fun `the predicate flags a testing import and a fake module call`() {
        val synthetic =
            """
            package x
            import $TESTING_PACKAGE.fakes.FakeClock
            val module = signalPorts()
            """.trimIndent()

        Assertions.assertEquals(
            listOf("import $TESTING_PACKAGE.fakes.FakeClock", "signalPorts("),
            fakeUses(synthetic),
        )
        Assertions.assertEquals(emptyList<String>(), fakeUses("// signalPorts() is not used here\nval x = 1"))
    }
}
