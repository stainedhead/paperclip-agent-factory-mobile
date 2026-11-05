package com.stainedhead.paperclipmobile.android.screens

/*
 * The lookups of the Approval screen (AND-041). Which actions are offered (by status, role and connectivity), when a
 * note is required and when the device owner is asked are the shared holder's; this only picks words and shapes text.
 */

private val acronyms =
    mapOf(
        "qa" to "QA",
        "cto" to "CTO",
        "ceo" to "CEO",
        "cfo" to "CFO",
        "pm" to "PM",
        "devops" to "DevOps",
        "ux" to "UX",
    )

/** "qa" reads "QA", "product_designer" reads "Product designer"; none for a role that is missing. */
fun roleName(raw: String?): String? {
    if (raw.isNullOrEmpty()) return null
    return acronyms[raw.lowercase()] ?: prettify(raw).replaceFirstChar { it.uppercase() }
}

/** A list as one bulleted text, none when it is empty. */
fun bullets(items: List<String>): String? = items.takeIf { it.isNotEmpty() }?.joinToString("\n") { "• $it" }
