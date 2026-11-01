package com.stainedhead.paperclipmobile.domain

/** What the signed-in person may do inside one company (PRD "Users and Roles"). */
enum class Role {
    Owner,
    Operator,
    Viewer,
    ;

    companion object {
        /**
         * Maps the server's membership role to an app role, failing closed (ADR-011): only an instance admin, `owner`
         * or `admin` is an Owner, only `operator` is an Operator, and everything else is a Viewer.
         */
        fun fromServer(
            membershipRole: String?,
            isInstanceAdmin: Boolean,
        ): Role =
            when {
                isInstanceAdmin -> Owner
                membershipRole == "owner" || membershipRole == "admin" -> Owner
                membershipRole == "operator" -> Operator
                else -> Viewer
            }
    }
}
