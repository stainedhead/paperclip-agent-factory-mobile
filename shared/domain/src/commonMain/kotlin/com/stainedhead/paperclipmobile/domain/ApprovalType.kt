package com.stainedhead.paperclipmobile.domain

/** The approval types of the pinned server plus [Unsupported] for types from a newer one. */
sealed interface ApprovalType {
    val serverName: String

    data object HireAgent : ApprovalType {
        override val serverName = "hire_agent"
    }

    data object ApproveCeoStrategy : ApprovalType {
        override val serverName = "approve_ceo_strategy"
    }

    data object BudgetOverrideRequired : ApprovalType {
        override val serverName = "budget_override_required"
    }

    data object RequestBoardApproval : ApprovalType {
        override val serverName = "request_board_approval"
    }

    data class Unsupported(
        val raw: String,
    ) : ApprovalType {
        override val serverName: String get() = raw
    }

    companion object {
        private val known = listOf(HireAgent, ApproveCeoStrategy, BudgetOverrideRequired, RequestBoardApproval)

        fun fromServer(raw: String): ApprovalType = known.firstOrNull { it.serverName == raw } ?: Unsupported(raw)
    }
}
