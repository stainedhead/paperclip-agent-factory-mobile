package com.stainedhead.paperclipmobile.architecture

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/** F-C-6: each scanning rule must fail on a planted violation of every form it claims to catch. */
class SourceScanningRulesTest {
    @Test
    fun `the public surface scan flags every modifier form`() {
        val planted =
            mapOf(
                "suspend fun leakToken() {}" to "leakToken",
                "inline fun <reified T> leakInline() {}" to "leakInline",
                "tailrec fun leakTail() {}" to "leakTail",
                "const val DEFAULT_KEY = \"x\"" to "DEFAULT_KEY",
                "lateinit var leakVar: String" to "leakVar",
                "public class Loud" to "Loud",
                "public suspend fun loudCall() {}" to "loudCall",
                "fun interface Callback { fun run() }" to "Callback",
                "annotation class Marker" to "Marker",
                "value class Wrapped(val x: Int)" to "Wrapped",
                "sealed interface Shape" to "Shape",
                "expect class Platform" to "Platform",
                "actual class Platform" to "Platform",
                "@Throws(Exception::class) fun annotated() {}" to "annotated",
                "@JvmInline value class Inline(val x: Int)" to "Inline",
                "fun <T> List<T>.extension() {}" to "extension",
                "typealias Alias = String" to "Alias",
                "enum class Mode { A }" to "Mode",
            )
        planted.forEach { (line, name) ->
            Assertions.assertEquals(listOf(name), publicTopLevel(line), "not flagged: $line")
        }
    }

    @Test
    fun `the public surface scan leaves internal, private and commented declarations alone`() {
        val quiet =
            listOf(
                "internal class Fine",
                "internal suspend fun fine() {}",
                "private const val SECRET_FREE = 1",
                "private fun x() {}",
                "expect internal class Platform",
                "actual internal class Platform",
                "internal actual fun fine()",
                "@Throws(Exception::class) internal fun annotated() {}",
                " * class InAComment",
                "    fun member() {}",
                "// fun commented() {}",
            )
        quiet.forEach { line -> Assertions.assertEquals(emptyList<String>(), publicTopLevel(line), "flagged: $line") }
    }

    @Test
    fun `the constructor scan is per class, one internal constructor does not excuse its neighbours`() {
        val source =
            """
            class SqlGood internal constructor(private val db: Db) {
            }

            class SqlLeaky(private val db: Db) {
            }

            class PaperclipPersistence internal constructor(
                val x: Int,
            )

            class Unrelated(val y: Int)
            """.trimIndent()
        val named = Regex("(Sql\\w*|PaperclipPersistence)")
        Assertions.assertEquals(listOf("SqlLeaky"), classesWithoutInternalConstructor(source, named))
    }

    @Test
    fun `the mention scan sees a star import, a qualified use and an aliased import but not a comment`() {
        val banned = listOf("NSUserDefaults", "NSFileManager")
        Assertions.assertEquals(
            listOf("val d = platform.Foundation.NSUserDefaults.standardUserDefaults"),
            codeMentions("// NSUserDefaults\n val d = platform.Foundation.NSUserDefaults.standardUserDefaults", banned),
        )
        Assertions.assertEquals(
            listOf("import platform.Foundation.NSFileManager as Files"),
            codeMentions("import platform.Foundation.NSFileManager as Files\n * NSFileManager in a kdoc", banned),
        )
        Assertions.assertEquals(
            listOf("platform.Foundation.*"),
            forbiddenImports(
                "import platform.Foundation.*\nimport platform.Security.*",
                listOf("platform.Foundation."),
            ),
        )
    }
}
