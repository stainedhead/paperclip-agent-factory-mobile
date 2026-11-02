package com.stainedhead.paperclipmobile.infrastructure.api.contract

import com.stainedhead.paperclipmobile.domain.AgentId
import com.stainedhead.paperclipmobile.domain.ApprovalId
import com.stainedhead.paperclipmobile.domain.CompanyId
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.infrastructure.api.ErrorMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * AD-050 (e): the error shapes the real server sends, compared with what the stubs and the mapper assume. Statuses
 * the research read from source are asserted; the ones it could not settle are recorded in the run output.
 */
class LiveErrorShapesTest {
    @Test
    fun `a bad key is Unauthorized and the error body is a json object with an error text`() =
        live {
            auth
                .meWithKey(
                    base,
                    Secret.of("pcp_board_TESTONLY_wrong"),
                ).verifiedError("AuthGateway.meWithKey", DomainError.Unauthorized)
            val reply = raw.call("GET", "/api/cli-auth/me", bearer = "pcp_board_TESTONLY_wrong")
            LiveReport.note("401 body", reply.body.take(MAX_BODY))
            assertEquals(UNAUTHORIZED, reply.status)
            assertNotNull(reply.string("error"), "The 401 body is not {\"error\": text}: ${reply.describe()}")
            assertEquals(DomainError.Unauthorized, ErrorMapper.fromStatus(reply.status, reply.body))
            val anonymous = raw.call("GET", "/api/companies/${scope.companyId.value}/issues")
            assertEquals(UNAUTHORIZED, anonymous.status, "An anonymous company read: ${anonymous.describe()}")
        }

    @Test
    fun `an unknown uuid is NotFound for issues, approvals and agents`() =
        live {
            val missing = UUID.randomUUID().toString()
            issues.get(scope, IssueId(missing)).verifiedError("IssueGateway.get", DomainError.NotFound)
            approvals.get(scope, ApprovalId(missing)).verifiedError("ApprovalGateway.get", DomainError.NotFound)
            agents.get(scope, AgentId(missing)).verifiedError("AgentGateway.get", DomainError.NotFound)
            val reply = raw.call("GET", "/api/issues/$missing", bearer = key)
            LiveReport.note("404 body", reply.body.take(MAX_BODY))
            assertEquals(NOT_FOUND, reply.status, reply.describe())
            assertNotNull(reply.string("error"), reply.describe())
        }

    @Test
    fun `a malformed id answers the status the stubs assume, recorded for each route`() =
        live {
            val routes =
                listOf(
                    "issues" to "/api/issues/not-a-uuid",
                    "approvals" to "/api/approvals/not-a-uuid",
                    "agents" to "/api/agents/not-a-uuid",
                    "runs" to "/api/heartbeat-runs/not-a-uuid",
                    "decisions" to "/api/decisions/not-a-uuid",
                )
            val replies = routes.associate { (name, path) -> name to raw.call("GET", path, bearer = key) }
            val seen = replies.mapValues { it.value.status }
            LiveReport.note("malformed id statuses", seen.toString())
            replies.forEach { (name, reply) -> LiveReport.note("malformed id body $name", reply.body.take(MAX_BODY)) }
            assertTrue(seen.values.all { it in CLIENT_OR_SERVER_ERROR }, "A malformed id succeeded: $seen")
            assertEquals(
                BAD_REQUEST,
                seen["runs"],
                "A malformed run id is 400 Invalid heartbeat run ID in the pinned source",
            )
        }

    @Test
    fun `a company the key cannot reach is refused and not leaked`() =
        live {
            val stranger = CompanyScope(scope.instanceId, CompanyId(UUID.randomUUID().toString()))
            val result =
                issues.list(
                    stranger,
                    com.stainedhead.paperclipmobile.domain
                        .IssueQuery(),
                )
            assertTrue(result is DomainResult.Failure, "A random company answered: $result")
            val reply = raw.call("GET", "/api/companies/${stranger.companyId.value}/issues", bearer = key)
            LiveReport.note("unknown company status", "${reply.status} -> ${(result as DomainResult.Failure).error}")
            LiveReport.note("unknown company body", reply.body.take(MAX_BODY))
            assertTrue(reply.status in listOf(FORBIDDEN, NOT_FOUND), reply.describe())
        }

    private companion object {
        const val MAX_BODY = 160
        const val BAD_REQUEST = 400
        const val UNAUTHORIZED = 401
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
        val CLIENT_OR_SERVER_ERROR = 400..599
    }
}
