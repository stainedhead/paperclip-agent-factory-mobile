package com.stainedhead.paperclipmobile.domain

/** App-generated identity of one configured factory (a Paperclip instance). */
data class InstanceId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "InstanceId must not be blank" }
    }
}

/** Server identity of a company inside one instance. */
data class CompanyId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "CompanyId must not be blank" }
    }
}
