package com.stainedhead.paperclipmobile.architecture

// Exact readings of a merged manifest, used by AndroidManifestRulesTest.

/** The permissions a manifest declares that are not the app's own (`<package>.…`), exactly as written. */
internal fun platformPermissions(xml: String): Set<String> {
    val manifest = parseXml(xml)
    val ownPrefix = manifest.getAttribute("package") + "."
    return manifest
        .children("uses-permission")
        .map { it.attr("name") }
        .filterNot { it.startsWith(ownPrefix) }
        .toSet()
}

/** The component class names of one tag in the application, for example `activity` or `provider`. */
internal fun componentNames(
    xml: String,
    tag: String,
): List<String> =
    parseXml(xml)
        .children("application")
        .single()
        .children(tag)
        .map { it.attr("name") }

/** One `android:` attribute of the application element, empty when absent. */
internal fun applicationAttribute(
    xml: String,
    name: String,
): String =
    parseXml(xml)
        .children("application")
        .single()
        .attr(name)
