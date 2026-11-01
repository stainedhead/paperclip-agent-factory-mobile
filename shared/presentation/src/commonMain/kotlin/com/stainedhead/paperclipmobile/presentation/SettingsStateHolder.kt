package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.auth.KeyExpiryStatus
import com.stainedhead.paperclipmobile.application.auth.ListBoardKeys
import com.stainedhead.paperclipmobile.application.auth.LoadInstanceAccess
import com.stainedhead.paperclipmobile.application.auth.RevokeBoardKey
import com.stainedhead.paperclipmobile.application.auth.RevokeKeyResult
import com.stainedhead.paperclipmobile.application.auth.SignOutEverywhere
import com.stainedhead.paperclipmobile.application.auth.SignOutInstance
import com.stainedhead.paperclipmobile.application.auth.SignOutResult
import com.stainedhead.paperclipmobile.application.instance.ObserveInstances
import com.stainedhead.paperclipmobile.application.settings.AppSettingsUpdate
import com.stainedhead.paperclipmobile.application.settings.ObserveAppSettings
import com.stainedhead.paperclipmobile.application.settings.UpdateAppSettings
import com.stainedhead.paperclipmobile.application.settings.UpdateSettingsResult
import com.stainedhead.paperclipmobile.domain.AppSettings
import com.stainedhead.paperclipmobile.domain.BoardKey
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.IdlePeriod
import com.stainedhead.paperclipmobile.domain.Instance
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.port.Connectivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsFactoryUi(
    val id: String,
    val name: String,
    val status: FactoryStatusUi,
    val expiry: ExpiryUi,
)

/** One of the account's board keys; [isCurrentDevice] marks this phone (PRD FR-025). */
data class DeviceKeyUi(
    val id: String,
    val name: String,
    val isCurrentDevice: Boolean,
    val lastUsedAtEpochMillis: Long?,
    val expiresAtEpochMillis: Long?,
)

enum class PairedDevicesStatus {
    Idle,
    Loading,
    Loaded,
    Failed,
}

data class PairedDevicesUi(
    val instanceId: String,
    val status: PairedDevicesStatus,
    val keys: List<DeviceKeyUi> = emptyList(),
)

/** A question the person must answer before something is signed out or revoked. */
sealed interface SettingsConfirmation {
    data class SignOut(
        val instanceId: String,
        val name: String,
    ) : SettingsConfirmation

    data object SignOutEverywhere : SettingsConfirmation

    data class RevokeKey(
        val instanceId: String,
        val keyId: String,
        val name: String,
        val isCurrentDevice: Boolean,
    ) : SettingsConfirmation
}

data class SettingsState(
    val deviceName: String = "",
    val deviceNameInvalid: Boolean = false,
    val appLockEnabled: Boolean = true,
    val idlePeriod: IdlePeriod = IdlePeriod.FiveMinutes,
    val idleOptions: List<IdlePeriod> = IdlePeriod.entries,
    /** Always true: destructive actions always ask the device owner and no setting changes that (PRD FR-014). */
    val destructiveActionAuthAlwaysOn: Boolean = true,
    val factories: List<SettingsFactoryUi> = emptyList(),
    val pairedDevices: PairedDevicesUi? = null,
    val profileName: String? = null,
    val profileEmail: String? = null,
    val confirmation: SettingsConfirmation? = null,
    val isBusy: Boolean = false,
    val isOffline: Boolean = false,
    val lastSignOutKey: ServerKeyOutcome? = null,
    val unconfirmedRevokes: Int = 0,
    val error: ErrorUi? = null,
)

sealed interface SettingsIntent {
    data class DeviceNameCommitted(
        val text: String,
    ) : SettingsIntent

    data class AppLockToggled(
        val enabled: Boolean,
    ) : SettingsIntent

    data class IdlePeriodChosen(
        val period: IdlePeriod,
    ) : SettingsIntent

    data class PairedDevicesRequested(
        val instanceId: String,
    ) : SettingsIntent

    data class SignOutRequested(
        val instanceId: String,
    ) : SettingsIntent

    data object SignOutEverywhereRequested : SettingsIntent

    data class RevokeKeyRequested(
        val instanceId: String,
        val keyId: String,
    ) : SettingsIntent

    data object ConfirmationAccepted : SettingsIntent

    data object ConfirmationDismissed : SettingsIntent

    data object MessageDismissed : SettingsIntent
}

data class SettingsUseCases(
    val observeSettings: ObserveAppSettings,
    val updateSettings: UpdateAppSettings,
    val observeInstances: ObserveInstances,
    val expiry: KeyExpiryStatus,
    val loadAccess: LoadInstanceAccess,
    val listKeys: ListBoardKeys,
    val revokeKey: RevokeBoardKey,
    val signOut: SignOutInstance,
    val signOutEverywhere: SignOutEverywhere,
)

/** State holder for Settings and profile (screen 13). */
class SettingsStateHolder(
    private val useCases: SettingsUseCases,
    private val connectivity: Connectivity,
    scope: CoroutineScope,
) {
    private val scope = childScope(scope)
    private val mutableState = MutableStateFlow(SettingsState())
    private val devices = SettingsDevices(useCases, connectivity, mutableState, this.scope)
    private val confirmations = SettingsConfirmations(useCases, mutableState, this.scope, devices)
    val state: StateFlow<SettingsState> = mutableState.asStateFlow()

    init {
        this.scope.launch {
            combine(useCases.observeSettings(), useCases.observeInstances(), connectivity.isOnline) { s, list, online ->
                confirmations.instances = list
                mutableState.update { it.fromSources(s, list, online) }
            }.collect {}
        }
    }

    fun onIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.DeviceNameCommitted -> {
                update(AppSettingsUpdate(deviceName = intent.text))
            }

            is SettingsIntent.AppLockToggled -> {
                update(AppSettingsUpdate(appLockEnabled = intent.enabled))
            }

            is SettingsIntent.IdlePeriodChosen -> {
                update(AppSettingsUpdate(idlePeriod = intent.period))
            }

            is SettingsIntent.PairedDevicesRequested -> {
                devices.load(intent.instanceId)
            }

            is SettingsIntent.SignOutRequested -> {
                confirmations.askSignOut(intent.instanceId)
            }

            SettingsIntent.SignOutEverywhereRequested -> {
                mutableState.update { it.copy(confirmation = SettingsConfirmation.SignOutEverywhere) }
            }

            is SettingsIntent.RevokeKeyRequested -> {
                confirmations.askRevoke(intent.instanceId, intent.keyId)
            }

            SettingsIntent.ConfirmationAccepted -> {
                confirmations.accept()
            }

            SettingsIntent.ConfirmationDismissed -> {
                mutableState.update { it.copy(confirmation = null) }
            }

            SettingsIntent.MessageDismissed -> {
                mutableState.update {
                    it.copy(error = null, lastSignOutKey = null, unconfirmedRevokes = 0, deviceNameInvalid = false)
                }
            }
        }
    }

    fun close() {
        scope.cancel()
    }

    private fun SettingsState.fromSources(
        s: AppSettings,
        list: List<Instance>,
        online: Boolean,
    ) = copy(
        deviceName = s.deviceName,
        appLockEnabled = s.appLockEnabled,
        idlePeriod = s.idlePeriod,
        factories = list.map { it.toFactoryUi() },
        isOffline = !online,
    )

    private fun Instance.toFactoryUi() =
        SettingsFactoryUi(id.value, displayName, status.toUi(), useCases.expiry(keyExpiresAt).toUi())

    private fun update(change: AppSettingsUpdate) {
        scope.launch {
            val result = useCases.updateSettings(change)
            mutableState.update { it.copy(deviceNameInvalid = result == UpdateSettingsResult.InvalidDeviceName) }
        }
    }
}

/** The paired devices list (this account's board keys) and the profile of the person who owns them. */
internal class SettingsDevices(
    private val useCases: SettingsUseCases,
    private val connectivity: Connectivity,
    private val state: MutableStateFlow<SettingsState>,
    private val scope: CoroutineScope,
) {
    fun load(instanceId: String) {
        if (!connectivity.isOnline.value) return
        val id = InstanceId(instanceId)
        val loading = PairedDevicesUi(instanceId, PairedDevicesStatus.Loading)
        state.update { it.copy(pairedDevices = loading, error = null) }
        scope.launch {
            val keys = useCases.listKeys(id)
            val access = useCases.loadAccess(id)
            state.update { current ->
                val profile = (access as? DomainResult.Success)?.value?.profile
                current.copy(
                    pairedDevices = keys.toDevices(instanceId),
                    profileName = profile?.name,
                    profileEmail = profile?.email,
                    error = (keys as? DomainResult.Failure)?.error?.toErrorUi(),
                )
            }
        }
    }

    private fun DomainResult<List<BoardKey>>.toDevices(instanceId: String): PairedDevicesUi =
        when (this) {
            is DomainResult.Failure -> {
                PairedDevicesUi(instanceId, PairedDevicesStatus.Failed)
            }

            is DomainResult.Success -> {
                PairedDevicesUi(
                    instanceId,
                    PairedDevicesStatus.Loaded,
                    value.map {
                        DeviceKeyUi(
                            it.id,
                            it.name,
                            it.isCurrentDevice,
                            it.lastUsedAt?.toEpochMilliseconds(),
                            it.expiresAt?.toEpochMilliseconds(),
                        )
                    },
                )
            }
        }
}

/** Sign out and revoke, each behind a confirmation. */
internal class SettingsConfirmations(
    private val useCases: SettingsUseCases,
    private val state: MutableStateFlow<SettingsState>,
    private val scope: CoroutineScope,
    private val devices: SettingsDevices,
) {
    var instances: List<Instance> = emptyList()

    fun askSignOut(instanceId: String) {
        val instance = instances.firstOrNull { it.id.value == instanceId } ?: return
        state.update { it.copy(confirmation = SettingsConfirmation.SignOut(instanceId, instance.displayName)) }
    }

    fun askRevoke(
        instanceId: String,
        keyId: String,
    ) {
        val key =
            state.value.pairedDevices
                ?.keys
                ?.firstOrNull { it.id == keyId } ?: return
        val confirmation = SettingsConfirmation.RevokeKey(instanceId, keyId, key.name, key.isCurrentDevice)
        state.update { it.copy(confirmation = confirmation) }
    }

    fun accept() {
        val confirmation = state.value.confirmation ?: return
        if (state.value.isBusy) return
        state.update { it.copy(isBusy = true, confirmation = null) }
        scope.launch {
            when (confirmation) {
                is SettingsConfirmation.SignOut -> signOut(confirmation.instanceId)
                SettingsConfirmation.SignOutEverywhere -> signOutEverywhere()
                is SettingsConfirmation.RevokeKey -> revoke(confirmation)
            }
        }
    }

    private suspend fun signOut(instanceId: String) {
        val result = useCases.signOut(InstanceId(instanceId))
        state.update {
            it.copy(
                isBusy = false,
                lastSignOutKey = (result as? SignOutResult.SignedOut)?.serverKey?.toUi(),
            )
        }
    }

    private suspend fun signOutEverywhere() {
        val result = useCases.signOutEverywhere()
        state.update { it.copy(isBusy = false, unconfirmedRevokes = result.unconfirmed) }
    }

    private suspend fun revoke(confirmation: SettingsConfirmation.RevokeKey) {
        val result = useCases.revokeKey(InstanceId(confirmation.instanceId), confirmation.keyId)
        state.update { it.afterRevoke(confirmation, result) }
        if (result == RevokeKeyResult.Revoked) devices.load(confirmation.instanceId)
    }

    private fun SettingsState.afterRevoke(
        confirmation: SettingsConfirmation.RevokeKey,
        result: RevokeKeyResult,
    ): SettingsState =
        when (result) {
            RevokeKeyResult.Revoked -> {
                copy(isBusy = false)
            }

            is RevokeKeyResult.SignedOutThisDevice -> {
                copy(
                    isBusy = false,
                    lastSignOutKey = result.serverKey.toUi(),
                    pairedDevices = PairedDevicesUi(confirmation.instanceId, PairedDevicesStatus.Idle),
                )
            }

            is RevokeKeyResult.Failed -> {
                copy(isBusy = false, error = result.error.toErrorUi())
            }
        }
}
