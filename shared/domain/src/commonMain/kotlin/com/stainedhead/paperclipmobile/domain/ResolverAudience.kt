package com.stainedhead.paperclipmobile.domain

enum class ResolverPolicy {
    Anyone,
    NotCreator,
    HumanOnly,
    ;

    companion object {
        /** Unknown policies fail closed to the narrowest audience. */
        fun fromServer(raw: String?): ResolverPolicy =
            when (raw) {
                "anyone", "board_or_agents" -> Anyone
                "not_creator" -> NotCreator
                else -> HumanOnly
            }
    }
}

enum class ResolverPolicySource {
    Requested,
    CompanyCap,
    GovernedAction,
}

/**
 * Who may resolve an issue-thread interaction, as the server evaluated it (research Q3). Facts, not copy. For a board
 * user only the addressee rule can be checked on the client; the creator rule is left to the server's 403.
 */
data class ResolverAudience(
    val requestedPolicy: ResolverPolicy,
    val effectivePolicy: ResolverPolicy,
    val policySource: ResolverPolicySource,
    val addresseeAgentId: String? = null,
    val addresseeUserId: String? = null,
    val addresseeName: String? = null,
    val createdByAgentId: String? = null,
    val createdByAgentName: String? = null,
) {
    /** False when the card is addressed to a different person. */
    fun allowsUser(userId: String?): Boolean = addresseeUserId == null || addresseeUserId == userId
}
