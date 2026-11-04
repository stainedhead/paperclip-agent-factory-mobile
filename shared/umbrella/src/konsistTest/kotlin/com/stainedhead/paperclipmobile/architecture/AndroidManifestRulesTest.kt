package com.stainedhead.paperclipmobile.architecture

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Android manifest security rules (AGENTS.md "Security Rules", PRD AND-002). They run on the merged manifest of each
 * variant, so a library that adds a permission or an exported component fails here and not in review. The merged
 * manifests come from the `processDebugManifest` and `processReleaseManifest` tasks that `konsistTest` depends on.
 */
class AndroidManifestRulesTest {
    private val androidNs = "http://schemas.android.com/apk/res/android"
    private val allowedPermissions =
        setOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.USE_BIOMETRIC",
        )
    private val launcherActivity = "com.stainedhead.paperclipmobile.android.MainActivity"
    private val componentTags = listOf("activity", "activity-alias", "service", "receiver", "provider")

    private fun parse(xml: String): Element {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        return factory
            .newDocumentBuilder()
            .parse(xml.byteInputStream())
            .documentElement
    }

    private fun Element.attr(name: String): String = getAttributeNS(androidNs, name)

    private fun Element.children(tag: String): List<Element> =
        (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }.filter { it.tagName == tag }

    private fun Element.isLauncher(): Boolean =
        children("intent-filter").any { filter ->
            filter.children("action").any { it.attr("name") == "android.intent.action.MAIN" } &&
                filter.children("category").any { it.attr("name") == "android.intent.category.LAUNCHER" }
        }

    /** Every way the merged manifest of one variant breaks the rules; empty when it is clean. */
    private fun findings(
        xml: String,
        debug: Boolean,
    ): List<String> {
        val manifest = parse(xml)
        val application = manifest.children("application").singleOrNull() ?: return listOf("no <application>")
        val findings = mutableListOf<String>()
        if (application.attr("allowBackup") != "false") findings += "allowBackup is not false"
        if (application.attr("dataExtractionRules").isEmpty()) findings += "no dataExtractionRules"
        if (application.attr("usesCleartextTraffic") != "false") findings += "usesCleartextTraffic is not false"
        val securityConfig = application.attr("networkSecurityConfig")
        if (!debug && securityConfig.isNotEmpty()) findings += "release names a networkSecurityConfig"
        if (debug && securityConfig.isEmpty()) findings += "debug has no networkSecurityConfig"
        if (!debug && application.attr("debuggable") == "true") findings += "release is debuggable"

        val ownPrefix = manifest.getAttribute("package") + "."
        manifest.children("uses-permission").map { it.attr("name") }.forEach {
            if (it !in allowedPermissions && !it.startsWith(ownPrefix)) findings += "permission $it"
        }

        val exported =
            componentTags.flatMap { tag -> application.children(tag) }.filter {
                it.attr("exported") == "true" ||
                    (it.attr("exported").isEmpty() && it.children("intent-filter").isNotEmpty())
            }
        val exportedNames = exported.map { it.attr("name") }
        if (exportedNames != listOf(launcherActivity)) {
            findings += "exported components $exportedNames (only the launcher Activity may be exported)"
        } else if (!exported.single().isLauncher()) {
            findings += "the exported component is not the launcher"
        }
        return findings
    }

    /** Hosts that may use cleartext in a network security config; `*` stands for a base config that allows it. */
    private fun cleartextHosts(xml: String): List<String> {
        val config = parse(xml)
        val base = config.children("base-config").firstOrNull()
        val hosts = mutableListOf<String>()
        if (base?.getAttribute("cleartextTrafficPermitted") == "true") hosts += "*"
        config.children("domain-config").filter { it.getAttribute("cleartextTrafficPermitted") == "true" }.forEach {
            hosts += it.children("domain").map { domain -> domain.textContent.trim() }
        }
        return hosts
    }

    private fun merged(variant: String): String {
        val path = requireNotNull(System.getProperty("pafm.mergedManifest.$variant")) { "no merged $variant manifest" }
        return File(path).readText()
    }

    @Test
    fun `the debug merged manifest follows the rules`() {
        Assertions.assertEquals(emptyList<String>(), findings(merged("debug"), debug = true))
    }

    @Test
    fun `the release merged manifest follows the rules`() {
        Assertions.assertEquals(emptyList<String>(), findings(merged("release"), debug = false))
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
                    <receiver android:name="com.example.Filtered"><intent-filter><action android:name="x" /></intent-filter></receiver>
                    <service android:name="com.example.Private" android:exported="false" />
                </application>
            </manifest>
            """.trimIndent()

        val found = findings(planted, debug = false)

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
                <domain-config cleartextTrafficPermitted="true"><domain>localhost</domain><domain>example.com</domain></domain-config>
                <domain-config cleartextTrafficPermitted="false"><domain>10.0.2.2</domain></domain-config>
            </network-security-config>
            """.trimIndent()

        Assertions.assertEquals(listOf("*", "localhost", "example.com"), cleartextHosts(planted))
    }
}
