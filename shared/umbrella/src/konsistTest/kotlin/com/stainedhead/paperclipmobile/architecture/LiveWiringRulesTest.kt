package com.stainedhead.paperclipmobile.architecture

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File

private val TESTING_PACKAGE = "$APP_PACKAGE.testing"

/** The module functions that bind fakes; the live graph must never call them (ADR-068). */
private val FAKE_MODULES = listOf("portsModule(", "demoPortsModule(", "sliceThreePorts(", "signalPorts(")

/** Code that is part of the live seam: it calls into the live graph or names the live platform. */
private val LIVE_SEAM = listOf("createLiveGraph(", "LivePlatform", "livePortsModule(")

/**
 * The functions of a file in which a fake module may legitimately appear because they build the DEMO graph; in the
 * file that holds the live entry point only the live entry point itself is scanned.
 */
private const val LIVE_ENTRY = "createLiveGraph"

private fun codeLines(source: String) =
    source.lines().filterNot { it.trim().startsWith("*") || it.trim().startsWith("//") || it.trim().startsWith("/*") }

/**
 * Every main-source file that is part of the live wiring: those whose name starts with `Live`, and any other file that
 * names the live seam in code (`PaperclipCore.kt` and any future file that calls `createLiveGraph`), in every source
 * set. Computed, not listed, so a new file is guarded without anyone remembering to add it.
 */
internal fun liveWiringSources(umbrellaSrc: File): List<File> =
    umbrellaSrc
        .walkTopDown()
        .filter { it.extension == "kt" && it.path.contains("Main/") }
        .filter { it.name.startsWith("Live") || codeLines(it.readText()).any { line -> LIVE_SEAM.any(line::contains) } }
        .toList()

/** The text of the function(s) named [name]: from the `fun` line to the next blank line or member. */
internal fun functionText(
    source: String,
    name: String,
): String {
    val lines = source.lines()
    return lines
        .indices
        .filter { lines[it].contains("fun $name(") }
        .joinToString("\n") { start ->
            val end =
                (start + 1 until lines.size).firstOrNull {
                    lines[it].isBlank() ||
                        lines[it].startsWith(
                            "    fun ",
                        )
                }
            lines.subList(start, end ?: lines.size).joinToString("\n")
        }
}

internal fun fakeUses(source: String): List<String> {
    val code = codeLines(source)
    val imports = code.map { it.trim() }.filter { it.startsWith("import $TESTING_PACKAGE") }
    val calls = FAKE_MODULES.filter { call -> code.any { line -> line.contains(call) } }
    return imports + calls
}

/** What the rule reports for [file]: a `Live*` file whole; any other file only its live entry point and imports. */
internal fun wiringOffenders(file: File): List<String> {
    val source = file.readText()
    if (file.name.startsWith("Live")) return fakeUses(source)
    val importsOnly = codeLines(source).map { it.trim() }.filter { it.startsWith("import $TESTING_PACKAGE") }
    return importsOnly + fakeUses(functionText(source, LIVE_ENTRY)).filterNot { it.startsWith("import") }
}

/**
 * The live graph (Phase 1b AD-040): the files that wire real adapters may not import the testing module nor call a
 * module function that binds fakes. The runtime twin of this rule is `LiveGraphTest`, which resolves every network and
 * persistence port and checks no fake is behind it. `PlatformServices.kt` holds the iOS Keychain test hook and does
 * not name the live seam, so it is not one of the guarded files.
 */
class LiveWiringRulesTest {
    private val umbrellaSrc = File(projectRoot, "shared/umbrella/src")

    @Test
    fun `the live wiring files import nothing from the testing module and bind no fakes`() {
        val files = liveWiringSources(umbrellaSrc)
        Assertions.assertTrue(files.isNotEmpty(), "No live wiring files found: the rule would pass vacuously")
        val offenders = files.associate { it.name to wiringOffenders(it) }.filterValues { it.isNotEmpty() }
        Assertions.assertTrue(offenders.isEmpty(), "Fakes in the live wiring: $offenders")
    }

    @Test
    fun `the rule selects the live graph files it is meant to guard`() {
        val names = liveWiringSources(umbrellaSrc).map { it.name }
        listOf("LiveGraph.kt", "LiveModules.kt", "LivePlatform.kt", "PaperclipCore.kt").forEach {
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

    @Test
    fun `the rule flags a testing import and a fake module in the live entry point of a non Live file`() {
        val root =
            kotlin.io.path
                .createTempDirectory("pafm-wiring")
                .toFile()
        try {
            val dir = File(root, "src/commonMain/kotlin").also { it.mkdirs() }
            File(dir, "Entry.kt").writeText(
                """
                import $TESTING_PACKAGE.fakes.FakeClock

                object Entry {
                    fun createDemo() = koinApplication { modules(demoPortsModule()) }

                    fun createLiveGraph(x: Int) = koinApplication { modules(portsModule()) }

                    fun other() = 1
                }
                """.trimIndent(),
            )
            val guarded = liveWiringSources(File(root, "src"))
            Assertions.assertEquals(listOf("Entry.kt"), guarded.map { it.name })
            Assertions.assertEquals(
                listOf("import $TESTING_PACKAGE.fakes.FakeClock", "portsModule("),
                wiringOffenders(guarded.single()),
            )
        } finally {
            root.deleteRecursively()
        }
    }
}
