package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult

/** Turns a ranged reply into a slice: where it starts and how big the file is, from `Content-Range` (RFC 9110). */
internal object AttachmentRanges {
    private val CONTENT_RANGE = Regex("^bytes (\\d+)-(\\d+)/(\\d+|\\*)$")

    /**
     * A 206 starts where `Content-Range` says; a 200 means the server ignored the range and sent the whole file, so it
     * starts at zero and its size is the size of the body. A 206 without a readable `Content-Range` is Malformed.
     */
    fun slice(
        reply: ApiBytes,
        requestedStart: Long,
    ): DomainResult<IssueGatewayAdapter.AttachmentSlice> {
        if (reply.status != PARTIAL) {
            val whole =
                IssueGatewayAdapter.AttachmentSlice(
                    reply.bytes,
                    0L,
                    reply.bytes.size.toLong(),
                    reply.contentType,
                )
            return DomainResult.Success(whole)
        }
        val match = reply.contentRange?.let { CONTENT_RANGE.matchEntire(it.trim()) }
        val start = match?.groupValues?.get(1)?.toLongOrNull()
        val end = match?.groupValues?.get(2)?.toLongOrNull()
        val total = match?.groupValues?.get(TOTAL)?.toLongOrNull()
        val fits = start != null && end != null && end - start + 1 == reply.bytes.size.toLong()
        if (start == null || !fits || start < requestedStart) return DomainResult.Failure(DomainError.Malformed)
        return DomainResult.Success(IssueGatewayAdapter.AttachmentSlice(reply.bytes, start, total, reply.contentType))
    }

    private const val PARTIAL = 206
    private const val TOTAL = 3
}
