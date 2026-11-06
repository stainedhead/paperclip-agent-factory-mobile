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
        Assertions.assertEquals(emptyList<String>(), manifestFindings(merged("releaseDemo"), debug = false))
    }

    @Test
    fun `every variant declares exactly the three permissions and nothing else from the platform`() {
        val expected =
            setOf(
                "android.permission.INTERNET",
                "android.permission.ACCESS_NETWORK_STATE",
                "android.permission.USE_BIOMETRIC",
            )
        listOf("debug", "release", "releaseDemo", "benchmark", "nonMinified").forEach {
            Assertions.assertEquals(expected, platformPermissions(merged(it)), "permissions of $it")
        }
    }

    @Test
    fun `the release manifests have no backup, no cleartext, no service, receiver or provider and one activity`() {
        listOf("release", "releaseDemo").forEach { variant ->
            val xml = merged(variant)
            Assertions.assertEquals("false", applicationAttribute(xml, "allowBackup"), variant)
            Assertions.assertEquals("false", applicationAttribute(xml, "usesCleartextTraffic"), variant)
            Assertions.assertEquals("", applicationAttribute(xml, "networkSecurityConfig"), variant)
            Assertions.assertEquals(listOf(LAUNCHER_ACTIVITY), componentNames(xml, "activity"), variant)
            listOf("service", "receiver", "activity-alias").forEach {
                Assertions.assertEquals(emptyList<String>(), componentNames(xml, it), "$it in $variant")
            }
            // androidx.startup (lifecycle's process observer and the profile installer start through it). Reviewed in
            // T8.7: it is not exported (exportedFindings fails the build otherwise) and carries no data of ours.
            Assertions.assertEquals(
                listOf("androidx.startup.InitializationProvider"),
                componentNames(xml, "provider"),
                variant,
            )
        }
    }

    @Test
    fun `the permission set is read exactly and an own permission is not counted`() {
        val xml =
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example.app">
                <uses-permission android:name="android.permission.INTERNET" />
                <uses-permission android:name="com.example.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" />
            </manifest>
            """.trimIndent()
        Assertions.assertEquals(setOf("android.permission.INTERNET"), platformPermissions(xml))
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
    fun `the Compose test host may be exported in a debug build only, and the profile receiver never`() {
        fun manifest(vararg components: String) =
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example.app">
                <application android:allowBackup="false" android:dataExtractionRules="@xml/r"
                    android:usesCleartextTraffic="false" android:networkSecurityConfig="@xml/n">
                    <activity android:name="$LAUNCHER_ACTIVITY" android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                    ${components.joinToString("\n")}
                </application>
            </manifest>
            """.trimIndent()
        val testHost = """<activity android:name="androidx.activity.ComponentActivity" android:exported="true" />"""
        val receiver =
            """<receiver android:name="androidx.profileinstaller.ProfileInstallReceiver" android:exported="true" />"""
        Assertions.assertEquals(emptyList<String>(), manifestFindings(manifest(testHost), debug = true))
        Assertions.assertTrue(
            manifestFindings(manifest(testHost), debug = false).any { it.contains("ComponentActivity") },
        )
        Assertions.assertTrue(manifestFindings(manifest(receiver), debug = true).any { it.contains("ProfileInstall") })
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
