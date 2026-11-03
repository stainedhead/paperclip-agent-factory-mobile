package com.stainedhead.paperclipmobile.infrastructure.api.contract

import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Secret
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** AD-050 (b): the real pairing flow end to end, with the board session approving as the web app would. */
class LivePairingTest {
    @Test
    fun `a device pairs, reads its account, and revokes another key and then its own`() =
        live {
            val device = Pairing.pair(auth, raw, session, base, "pafm contract device")
            LiveReport.record("AuthGateway.createChallenge", "", hasData = true)
            LiveReport.record("AuthGateway.pollChallenge", "(pending then approved)", hasData = true)

            val access = auth.meWithKey(base, Secret.of(device.key)).verified("AuthGateway.meWithKey")
            assertEquals(ServerBootstrap.EMAIL, access.profile.email)
            val keyId = checkNotNull(access.keyId) { "The key validated but the server reported no key id" }
            assertTrue(access.profile.isInstanceAdmin)

            val keys =
                auth
                    .listBoardKeysWithKey(
                        base,
                        Secret.of(device.key),
                    ).verified("AuthGateway.listBoardKeysWithKey")
            assertTrue(keys.any { it.id == keyId }, "The new key is missing from its own key list")

            val second = registerDevice("contract-device-2", device.key, keyId)
            val known = auth.me(second).verified("AuthGateway.me")
            assertEquals(keyId, known.keyId)
            val companies = auth.listCompanies(second).verified("AuthGateway.listCompanies")
            assertTrue(
                companies.any { it.id == scope.companyId },
                "The seeded company is not listed for the paired key",
            )
            val ownKeys = auth.listBoardKeys(second).verified("AuthGateway.listBoardKeys")
            assertTrue(
                ownKeys.any { it.id == keyId && it.isCurrentDevice },
                "The current device is not marked in the key list",
            )

            // Another key of the same account is revoked by id, then the device revokes its own key.
            val other = Pairing.pair(auth, raw, session, base, "pafm contract throwaway")
            val otherId =
                checkNotNull(auth.meWithKey(base, Secret.of(other.key)).verified("AuthGateway.meWithKey").keyId)
            auth.revokeBoardKey(second, otherId).verified("AuthGateway.revokeBoardKey")
            auth.meWithKey(base, Secret.of(other.key)).verifiedError("AuthGateway.meWithKey", DomainError.Unauthorized)

            auth.revokeCurrentKey(second).verified("AuthGateway.revokeCurrentKey")
            val after = auth.me(second)
            assertTrue(
                after is DomainResult.Failure && after.error == DomainError.Unauthorized,
                "A revoked key still works: $after",
            )
            auth.meWithKey(base, Secret.of(device.key)).verifiedError("AuthGateway.meWithKey", DomainError.Unauthorized)
        }
}
