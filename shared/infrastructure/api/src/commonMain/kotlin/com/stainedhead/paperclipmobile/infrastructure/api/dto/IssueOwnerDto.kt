package com.stainedhead.paperclipmobile.infrastructure.api.dto

import kotlinx.serialization.Serializable

/** The two fields of `GET /issues/{id}` that prove whose issue it is; the rest of the (large) reply is not read. */
@Serializable
internal data class IssueOwnerDto(
    val id: String? = null,
    val companyId: String? = null,
)
