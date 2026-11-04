package com.stainedhead.paperclipmobile.architecture

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The two on-demand benchmark build types (`benchmark`, `nonMinified`; ADR-135) follow the release manifest rules
 * except for one reviewed exception: the profile installer's receiver, guarded by the DUMP permission that only the
 * shell holds.
 */
class AndroidBenchmarkManifestRulesTest {
    private fun merged(variant: String): String {
        val path = requireNotNull(System.getProperty("pafm.mergedManifest.$variant")) { "no merged $variant manifest" }
        return File(path).readText()
    }

    @Test
    fun `the benchmark variants follow the release rules except for the DUMP-guarded profile receiver`() {
        listOf("benchmark", "nonMinified").forEach { variant ->
            val xml = merged(variant)
            Assertions.assertEquals(
                emptyList<String>(),
                manifestFindings(xml, debug = false, profiling = true),
                variant,
            )
            Assertions.assertEquals(listOf(LAUNCHER_ACTIVITY), componentNames(xml, "activity"), variant)
            Assertions.assertEquals(listOf(PROFILE_RECEIVER), componentNames(xml, "receiver"), variant)
            Assertions.assertEquals(emptyList<String>(), componentNames(xml, "service"), variant)
            Assertions.assertEquals("false", applicationAttribute(xml, "allowBackup"), variant)
            Assertions.assertEquals("false", applicationAttribute(xml, "usesCleartextTraffic"), variant)
            Assertions.assertEquals("", applicationAttribute(xml, "networkSecurityConfig"), variant)
            // Macrobenchmark needs a profileable, not a debuggable, app: debuggable would measure the wrong thing.
            Assertions.assertTrue(
                "<profileable" in xml && "android:shell=\"true\"" in xml,
                "no profileable in $variant",
            )
        }
    }

    @Test
    fun `the profile receiver is allowed in a benchmark variant only and only behind the DUMP permission`() {
        fun manifest(receiver: String) =
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example.app">
                <application android:allowBackup="false" android:dataExtractionRules="@xml/r"
                    android:usesCleartextTraffic="false">
                    <activity android:name="$LAUNCHER_ACTIVITY" android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                    $receiver
                </application>
            </manifest>
            """.trimIndent()
        val guarded =
            """<receiver android:name="$PROFILE_RECEIVER" android:exported="true"
                android:permission="$DUMP_PERMISSION" />"""
        val open = """<receiver android:name="$PROFILE_RECEIVER" android:exported="true" />"""
        val other = """<receiver android:name="com.example.Other" android:exported="true" />"""

        Assertions.assertEquals(
            emptyList<String>(),
            manifestFindings(manifest(guarded), debug = false, profiling = true),
        )
        Assertions.assertTrue(manifestFindings(manifest(guarded), debug = false).any { it.contains("ProfileInstall") })
        Assertions.assertTrue(manifestFindings(manifest(open), debug = false, profiling = true).isNotEmpty())
        Assertions.assertTrue(manifestFindings(manifest(other), debug = false, profiling = true).isNotEmpty())
    }
}
