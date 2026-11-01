package com.stainedhead.paperclipmobile.domain

/** Which factory and which company a call is for. Every company-scoped gateway call carries one (AGENTS.md). */
data class CompanyScope(
    val instanceId: InstanceId,
    val companyId: CompanyId,
)

/** A scope plus what the signed-in person may do in it. Mutation use cases take this (ADR-019). */
data class CompanyContext(
    val scope: CompanyScope,
    val role: Role,
    val userId: String? = null,
)
