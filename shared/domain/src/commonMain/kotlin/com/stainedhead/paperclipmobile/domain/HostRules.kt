package com.stainedhead.paperclipmobile.domain

/** Host rules of [BaseUri.parse]: DNS labels, canonical IPv4 and well-formed bracketed IPv6 (review F-A-10). */
internal object HostRules {
    private const val MAX_HOST_LENGTH = 253
    private const val MAX_LABEL_LENGTH = 63
    private const val IPV4_PARTS = 4
    private const val MAX_IPV4_PART = 255
    private const val MAX_IPV4_PART_DIGITS = 3
    private const val IPV6_GROUPS = 8
    private const val MAX_IPV6_GROUP_DIGITS = 4
    private val LABEL = Regex("[a-z0-9]([a-z0-9-]*[a-z0-9])?")

    /** [host] is already lower-cased; an IPv6 literal keeps its brackets. */
    fun isValid(host: String): Boolean =
        when {
            host.startsWith('[') -> isValidIpv6(host)
            host.length > MAX_HOST_LENGTH -> false
            else -> host.split('.').let { labels -> labels.all(::isValidLabel) && !isBrokenIpv4(labels) }
        }

    private fun isValidLabel(label: String): Boolean = label.length <= MAX_LABEL_LENGTH && LABEL.matches(label)

    /** A host that ends in a digits-only label is an IPv4 literal and must be a canonical one. */
    private fun isBrokenIpv4(labels: List<String>): Boolean =
        labels.last().all { it in '0'..'9' } && !(labels.size == IPV4_PARTS && labels.all(::isIpv4Part))

    private fun isIpv4Part(part: String): Boolean =
        part.length in 1..MAX_IPV4_PART_DIGITS && part.all { it in '0'..'9' } &&
            (part == "0" || !part.startsWith('0')) && part.toInt() <= MAX_IPV4_PART

    private fun isValidIpv6(host: String): Boolean {
        if (!host.endsWith(']')) return false
        val address = host.substring(1, host.length - 1)
        val parts = address.split("::")
        if (address.isEmpty() || parts.size > 2) return false
        val groups = parts.flatMap { if (it.isEmpty()) emptyList() else it.split(':') }
        val trailingV4 = groups.lastOrNull()?.contains('.') == true
        val size = groups.size + if (trailingV4) 1 else 0
        val shapeOk = if (parts.size == 2) size < IPV6_GROUPS else size == IPV6_GROUPS
        val edgesOk =
            (!address.startsWith(':') || address.startsWith("::")) &&
                (!address.endsWith(':') || address.endsWith("::"))
        return shapeOk && edgesOk &&
            groups.withIndex().all { (index, group) ->
                if (trailingV4 && index == groups.lastIndex) isIpv4Address(group) else isIpv6Group(group)
            }
    }

    private fun isIpv4Address(text: String): Boolean =
        text.split('.').let { it.size == IPV4_PARTS && it.all(::isIpv4Part) }

    private fun isIpv6Group(group: String): Boolean =
        group.length in 1..MAX_IPV6_GROUP_DIGITS && group.all { it in '0'..'9' || it in 'a'..'f' }
}
