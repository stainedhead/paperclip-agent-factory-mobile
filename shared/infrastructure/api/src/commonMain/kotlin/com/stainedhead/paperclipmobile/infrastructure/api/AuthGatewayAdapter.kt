package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.BaseUri
import com.stainedhead.paperclipmobile.domain.BoardAccess
import com.stainedhead.paperclipmobile.domain.BoardKey
import com.stainedhead.paperclipmobile.domain.ChallengeStatus
import com.stainedhead.paperclipmobile.domain.CompanyId
import com.stainedhead.paperclipmobile.domain.CompanyRef
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.PairingChallenge
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.port.AuthGateway
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import com.stainedhead.paperclipmobile.infrastructure.api.dto.AckDto
import com.stainedhead.paperclipmobile.infrastructure.api.dto.BoardKeyDto
import com.stainedhead.paperclipmobile.infrastructure.api.dto.ChallengeCreatedDto
import com.stainedhead.paperclipmobile.infrastructure.api.dto.ChallengePolledDto
import com.stainedhead.paperclipmobile.infrastructure.api.dto.CompanyDto
import com.stainedhead.paperclipmobile.infrastructure.api.dto.CreateChallengeBody
import com.stainedhead.paperclipmobile.infrastructure.api.dto.MeDto
import com.stainedhead.paperclipmobile.infrastructure.api.dto.toDomain
import io.ktor.http.HttpMethod
import kotlinx.serialization.encodeToString

/**
 * The authentication endpoints of Paperclip (AD-011). Calls with a key that is not stored yet (`meWithKey`,
 * `listBoardKeysWithKey`) go to the host they are given and nowhere else; calls for a configured factory read that
 * factory's own address and key. Pairing endpoints carry no credential.
 */
class AuthGatewayAdapter(
    private val client: PaperclipHttpClient,
    instances: InstanceRepository,
    secrets: SecretStore,
) : AuthGateway {
    private val stored = StoredCredentials(instances, secrets)

    override suspend fun createChallenge(
        baseUri: BaseUri,
        clientName: String,
        requestedCompanyId: CompanyId?,
    ): DomainResult<PairingChallenge> {
        val body = CreateChallengeBody(PAIRING_COMMAND, clientName, BOARD_ACCESS, requestedCompanyId?.value)
        val request =
            ApiRequest(Destination(baseUri), HttpMethod.Post, CHALLENGES, jsonBody = ApiJson.encodeToString(body))
        return client.send(request).decode<ChallengeCreatedDto>().map { it.toDomain(baseUri) }
    }

    override suspend fun pollChallenge(
        baseUri: BaseUri,
        challengeId: String,
        challengeToken: Secret,
    ): DomainResult<ChallengeStatus> {
        // The challenge token is the server's own query parameter (access.ts:2812); it goes to this host only.
        val request =
            ApiRequest(
                Destination(baseUri),
                HttpMethod.Get,
                CHALLENGES + challengeId,
                query = listOf("token" to challengeToken.reveal()),
            )
        return client.send(request).decode<ChallengePolledDto>().map { it.toDomain() }
    }

    override suspend fun meWithKey(
        baseUri: BaseUri,
        key: Secret,
    ): DomainResult<BoardAccess> =
        client
            .send(ApiRequest(Destination(baseUri), HttpMethod.Get, ME, bearer = key))
            .decode<MeDto>()
            .map { it.toDomain() }

    override suspend fun me(instanceId: InstanceId): DomainResult<BoardAccess> =
        stored.call(instanceId) { instance, key ->
            client
                .send(ApiRequest(Destination(instance.baseUri, instance.pins), HttpMethod.Get, ME, bearer = key))
                .decode<MeDto>()
                .map { it.toDomain() }
        }

    override suspend fun listCompanies(instanceId: InstanceId): DomainResult<List<CompanyRef>> =
        stored.call(instanceId) { instance, key ->
            val request =
                ApiRequest(
                    Destination(instance.baseUri, instance.pins),
                    HttpMethod.Get,
                    listOf("companies"),
                    query = listOf("scope" to "accessible"),
                    bearer = key,
                )
            client.send(request).decode<List<CompanyDto>>().map { list -> list.map { it.toDomain() } }
        }

    override suspend fun listBoardKeys(instanceId: InstanceId): DomainResult<List<BoardKey>> =
        stored.call(instanceId) { instance, key ->
            val request =
                ApiRequest(Destination(instance.baseUri, instance.pins), HttpMethod.Get, KEYS, bearer = key)
            client.send(request).decode<List<BoardKeyDto>>().map { list ->
                list.map { it.toDomain(currentKeyId = instance.boardKeyId) }
            }
        }

    override suspend fun listBoardKeysWithKey(
        baseUri: BaseUri,
        key: Secret,
    ): DomainResult<List<BoardKey>> =
        client
            .send(ApiRequest(Destination(baseUri), HttpMethod.Get, KEYS, bearer = key))
            .decode<List<BoardKeyDto>>()
            .map { list -> list.map { it.toDomain(currentKeyId = null) } }

    override suspend fun revokeBoardKey(
        instanceId: InstanceId,
        keyId: String,
    ): DomainResult<Unit> {
        // The server answers 400 for an id that is not a UUID (access.ts:2959); such a key cannot exist.
        if (!UUID.matches(keyId)) return DomainResult.Failure(DomainError.NotFound)
        return stored.call(instanceId) { instance, key ->
            val request =
                ApiRequest(Destination(instance.baseUri, instance.pins), HttpMethod.Delete, KEYS + keyId, bearer = key)
            client.send(request).decode<AckDto>().map { Unit }
        }
    }

    override suspend fun revokeCurrentKey(instanceId: InstanceId): DomainResult<Unit> =
        stored.call(instanceId) { instance, key ->
            val request =
                ApiRequest(
                    Destination(instance.baseUri, instance.pins),
                    HttpMethod.Post,
                    listOf("cli-auth", "revoke-current"),
                    bearer = key,
                )
            client.send(request).decode<AckDto>().map { Unit }
        }

    private companion object {
        const val PAIRING_COMMAND = "paperclip-mobile pair"
        const val BOARD_ACCESS = "board"
        val CHALLENGES = listOf("cli-auth", "challenges")
        val ME = listOf("cli-auth", "me")
        val KEYS = listOf("board-api-keys")
        val UUID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    }
}
