package com.stainedhead.paperclipmobile.architecture

import org.w3c.dom.Element
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** XML scanning behind `AndroidManifestRulesTest`; the planted-violation tests live there. */
private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
internal const val LAUNCHER_ACTIVITY = "com.stainedhead.paperclipmobile.android.MainActivity"

private val ALLOWED_PERMISSIONS =
    setOf(
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.USE_BIOMETRIC",
    )
private val COMPONENT_TAGS = listOf("activity", "activity-alias", "service", "receiver", "provider")

internal fun parseXml(xml: String): Element {
    val factory = DocumentBuilderFactory.newInstance()
    factory.isNamespaceAware = true
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    return factory
        .newDocumentBuilder()
        .parse(xml.byteInputStream())
        .documentElement
}

private fun Element.attr(name: String): String = getAttributeNS(ANDROID_NS, name)

private fun Element.children(tag: String): List<Element> =
    (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }.filter { it.tagName == tag }

private fun Element.isLauncher(): Boolean =
    children("intent-filter").any { filter ->
        filter.children("action").any { it.attr("name") == "android.intent.action.MAIN" } &&
            filter.children("category").any { it.attr("name") == "android.intent.category.LAUNCHER" }
    }

private fun applicationFindings(
    application: Element,
    debug: Boolean,
): List<String> {
    val securityConfig = application.attr("networkSecurityConfig")
    return listOfNotNull(
        "allowBackup is not false".takeIf { application.attr("allowBackup") != "false" },
        "no dataExtractionRules".takeIf { application.attr("dataExtractionRules").isEmpty() },
        "usesCleartextTraffic is not false".takeIf { application.attr("usesCleartextTraffic") != "false" },
        "release names a networkSecurityConfig".takeIf { !debug && securityConfig.isNotEmpty() },
        "debug has no networkSecurityConfig".takeIf { debug && securityConfig.isEmpty() },
        "release is debuggable".takeIf { !debug && application.attr("debuggable") == "true" },
    )
}

private fun permissionFindings(manifest: Element): List<String> {
    val ownPrefix = manifest.getAttribute("package") + "."
    return manifest
        .children("uses-permission")
        .map { it.attr("name") }
        .filter { it !in ALLOWED_PERMISSIONS && !it.startsWith(ownPrefix) }
        .map { "permission $it" }
}

private fun exportedFindings(application: Element): List<String> {
    val exported =
        COMPONENT_TAGS.flatMap { application.children(it) }.filter {
            it.attr("exported") == "true" ||
                (it.attr("exported").isEmpty() && it.children("intent-filter").isNotEmpty())
        }
    val names = exported.map { it.attr("name") }
    return when {
        names != listOf(LAUNCHER_ACTIVITY) -> {
            listOf("exported components $names (only the launcher Activity may be exported)")
        }

        !exported.single().isLauncher() -> {
            listOf("the exported component is not the launcher")
        }

        else -> {
            emptyList()
        }
    }
}

/** Every way the merged manifest of one variant breaks the rules; empty when it is clean. */
internal fun manifestFindings(
    xml: String,
    debug: Boolean,
): List<String> {
    val manifest = parseXml(xml)
    val application = manifest.children("application").singleOrNull() ?: return listOf("no <application>")
    return applicationFindings(application, debug) + permissionFindings(manifest) + exportedFindings(application)
}

/** Hosts that may use cleartext in a network security config; `*` stands for a base config that allows it. */
internal fun cleartextHosts(xml: String): List<String> {
    val config = parseXml(xml)
    val base = config.children("base-config").firstOrNull()
    val hosts = mutableListOf<String>()
    if (base?.getAttribute("cleartextTrafficPermitted") == "true") hosts += "*"
    config.children("domain-config").filter { it.getAttribute("cleartextTrafficPermitted") == "true" }.forEach {
        hosts += it.children("domain").map { domain -> domain.textContent.trim() }
    }
    return hosts
}
