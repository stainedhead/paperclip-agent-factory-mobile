package com.stainedhead.paperclipmobile.architecture

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Android manifest security rules (AGENTS.md "Security Rules", PRD AND-002). They run on the merged manifest of each
 * variant, so a library that adds a permission or an exported component fails here and not in review. The merged
 * manifests come from the `processDebugManifest` and `processReleaseManifest` tasks that `konsistTest` depends on.
 */
class AndroidManifestRulesTest {
    private fun merged(variant: String): String {
        val path = requireNotNull(System.getProperty("pafm.mergedManifest.$variant")) { "no merged $variant manifest" }
        return File(path).readText()
    }

    @Test
    fun `the debug merged manifest follows the rules`() {
        Assertions.assertEquals(emptyList<String>(), manifestFindings(merged("debug"), debug = true))
    }

    @Test
    fun `the release merged manifest follows the rules`() {
        Assertions.assertEquals(emptyList<String>(), manifestFindings(merged("release"), debug = false))
    }

    @Test
    fun `the debug network security config allows cleartext only for localhost and the emulator host`() {
        val config = File(projectRoot, "androidApp/src/debug/res/xml/network_security_config.xml")
        Assertions.assertTrue(config.isFile, "debug network security config is missing")
        Assertions.assertEquals(listOf("localhost", "10.0.2.2"), cleartextHosts(config.readText()))
    }

    @Test
    fun `the manifest scan flags every planted violation`() {
        val planted =
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example.app">
                <uses-permission android:name="android.permission.CAMERA" />
                <uses-permission android:name="android.permission.INTERNET" />
                <uses-permission android:name="com.example.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" />
                <application android:allowBackup="true" android:debuggable="true">
                    <activity android:name="com.example.Other" android:exported="true" />
                    <receiver android:name="com.example.Filtered">
                        <intent-filter><action android:name="x" /></intent-filter>
                    </receiver>
                    <service android:name="com.example.Private" android:exported="false" />
                </application>
            </manifest>
            """.trimIndent()

        val found = manifestFindings(planted, debug = false)

        Assertions.assertTrue("allowBackup is not false" in found, "$found")
        Assertions.assertTrue("no dataExtractionRules" in found, "$found")
        Assertions.assertTrue("usesCleartextTraffic is not false" in found, "$found")
        Assertions.assertTrue("release is debuggable" in found, "$found")
        Assertions.assertTrue("permission android.permission.CAMERA" in found, "$found")
        Assertions.assertTrue(found.none { it.contains("INTERNET") || it.contains("DYNAMIC_RECEIVER") }, "$found")
        Assertions.assertTrue(found.any { it.startsWith("exported components") && it.contains("Filtered") }, "$found")
    }

    @Test
    fun `the cleartext scan flags a base config and an extra domain`() {
        val planted =
            """
            <network-security-config>
                <base-config cleartextTrafficPermitted="true" />
                <domain-config cleartextTrafficPermitted="true">
                    <domain>localhost</domain><domain>example.com</domain>
                </domain-config>
                <domain-config cleartextTrafficPermitted="false"><domain>10.0.2.2</domain></domain-config>
            </network-security-config>
            """.trimIndent()

        Assertions.assertEquals(listOf("*", "localhost", "example.com"), cleartextHosts(planted))
    }
}
