package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.ValidateInstanceAddress
import com.stainedhead.paperclipmobile.application.auth.KeyExpiryStatus
import com.stainedhead.paperclipmobile.application.auth.LoadInstanceAccess
import com.stainedhead.paperclipmobile.application.auth.PairInstance
import com.stainedhead.paperclipmobile.application.auth.SignInResult
import com.stainedhead.paperclipmobile.application.auth.SignOutInstance
import com.stainedhead.paperclipmobile.application.auth.SignOutResult
import com.stainedhead.paperclipmobile.application.instance.AddInstance
import com.stainedhead.paperclipmobile.application.instance.AddInstanceResult
import com.stainedhead.paperclipmobile.application.instance.GetInstance
import com.stainedhead.paperclipmobile.application.instance.InstanceEdit
import com.stainedhead.paperclipmobile.application.instance.InstanceNameIssue
import com.stainedhead.paperclipmobile.application.instance.ProbeInstance
import com.stainedhead.paperclipmobile.application.instance.ProbeResult
import com.stainedhead.paperclipmobile.application.instance.RemoveInstance
import com.stainedhead.paperclipmobile.application.instance.RemoveInstanceResult
import com.stainedhead.paperclipmobile.application.instance.UpdateInstance
import com.stainedhead.paperclipmobile.application.instance.UpdateInstanceResult
import com.stainedhead.paperclipmobile.domain.BaseUriResult
import com.stainedhead.paperclipmobile.domain.CompanyId
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Exposure
import com.stainedhead.paperclipmobile.domain.HealthReport
import com.stainedhead.paperclipmobile.domain.Instance
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import com.stainedhead.paperclipmobile.domain.ProbeRefusal
import com.stainedhead.paperclipmobile.domain.SpkiPin
import com.stainedhead.paperclipmobile.domain.port.Connectivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class EditMode {
    Add,
    Edit,
}

enum class EditConfirmation {
    SignOut,
    Remove,
}

data class EditInstanceState(
    val mode: EditMode = EditMode.Add,
    val instanceId: String? = null,
    val name: String = "",
    val nameIssue: InstanceNameIssue? = null,
    val address: String = "",
    val addressIssue: AddressIssue? = null,
    /** True when the address differs from the saved one: saving drops the stored key (ADR-012). */
    val addressChangeClearsKey: Boolean = false,
    val probe: ProbeStatus = ProbeStatus.Idle,
    val probeRefusal: ProbeRefusal? = null,
    val serverVersion: String? = null,
    val exposure: Exposure? = null,
    val status: FactoryStatusUi? = null,
    val expiry: ExpiryUi? = null,
    val defaultCompanyId: String? = null,
    val companies: List<CompanyUi> = emptyList(),
    val profileName: String? = null,
    val profileEmail: String? = null,
    val pins: List<String> = emptyList(),
    val pinningEnabled: Boolean = false,
    val pinEntryOpen: Boolean = false,
    val pinInvalid: Boolean = false,
    val pairing: PairingStatus = PairingStatus.Idle,
    val confirmation: EditConfirmation? = null,
    val deviceAuthIssue: DeviceAuthIssue? = null,
    val serverKeyOutcome: ServerKeyOutcome? = null,
    val isOffline: Boolean = false,
    val isBusy: Boolean = false,
    val canSave: Boolean = false,
    val saved: Boolean = false,
    val removed: Boolean = false,
    val error: ErrorUi? = null,
)

sealed interface EditInstanceIntent {
    data class NameChanged(
        val text: String,
    ) : EditInstanceIntent

    data class AddressChanged(
        val text: String,
    ) : EditInstanceIntent

    data object ProbeRequested : EditInstanceIntent

    data class DefaultCompanyChosen(
        val companyId: String,
    ) : EditInstanceIntent

    data class PinAdded(
        val text: String,
    ) : EditInstanceIntent

    data class PinRemoved(
        val pin: String,
    ) : EditInstanceIntent

    data class PinningToggled(
        val enabled: Boolean,
    ) : EditInstanceIntent

    data object SaveRequested : EditInstanceIntent

    data object RePairRequested : EditInstanceIntent

    data object PairCancelled : EditInstanceIntent

    data object SignOutRequested : EditInstanceIntent

    data object RemoveRequested : EditInstanceIntent

    data object ConfirmationAccepted : EditInstanceIntent

    data object ConfirmationDismissed : EditInstanceIntent

    data object MessageDismissed : EditInstanceIntent
}

data class EditInstanceUseCases(
    val validateAddress: ValidateInstanceAddress,
    val get: GetInstance,
    val probe: ProbeInstance,
    val add: AddInstance,
    val update: UpdateInstance,
    val loadAccess: LoadInstanceAccess,
    val pair: PairInstance,
    val signOut: SignOutInstance,
    val remove: RemoveInstance,
    val expiry: KeyExpiryStatus,
)

/**
 * State holder for the Add / edit factory screen (screen 9). Pass `null` as [instanceId] to add a factory.
 * It shows the probe result, the default company, the person's read-only access and roles, certificate pins, and offers
 * re-pair, sign out and remove.
 */
class EditInstanceStateHolder(
    instanceId: String?,
    private val useCases: EditInstanceUseCases,
    private val connectivity: Connectivity,
    scope: CoroutineScope,
) {
    private val scope = childScope(scope)
    private val mutableState = MutableStateFlow(EditInstanceState())
    private val actions = EditInstanceActions(useCases, mutableState, this.scope)
    val state: StateFlow<EditInstanceState> = mutableState.asStateFlow()

    init {
        this.scope.launch {
            connectivity.isOnline.collect { online ->
                mutableState.update { it.copy(isOffline = !online) }
            }
        }
        if (instanceId != null) actions.load(InstanceId(instanceId))
    }

    fun onIntent(intent: EditInstanceIntent) {
        if (!fieldIntent(intent)) actionIntent(intent)
    }

    fun close() {
        actions.pairing.cancel()
        scope.cancel()
    }

    private fun fieldIntent(intent: EditInstanceIntent): Boolean {
        when (intent) {
            is EditInstanceIntent.NameChanged -> {
                mutableState.update { it.copy(name = intent.text, nameIssue = null).revalidated() }
            }

            is EditInstanceIntent.AddressChanged -> {
                addressChanged(intent.text)
            }

            is EditInstanceIntent.DefaultCompanyChosen -> {
                mutableState.update { it.copy(defaultCompanyId = intent.companyId) }
            }

            is EditInstanceIntent.PinAdded -> {
                actions.fields.addPin(intent.text)
            }

            is EditInstanceIntent.PinRemoved -> {
                actions.fields.removePin(intent.pin)
            }

            is EditInstanceIntent.PinningToggled -> {
                actions.fields.togglePinning(intent.enabled)
            }

            else -> {
                return false
            }
        }
        return true
    }

    private fun actionIntent(intent: EditInstanceIntent) {
        when (intent) {
            EditInstanceIntent.ProbeRequested -> {
                online { actions.fields.probe() }
            }

            EditInstanceIntent.SaveRequested -> {
                actions.saving.save()
            }

            EditInstanceIntent.RePairRequested -> {
                online { actions.pairing.pair() }
            }

            EditInstanceIntent.PairCancelled -> {
                actions.pairing.cancel()
            }

            EditInstanceIntent.SignOutRequested -> {
                actions.teardown.ask(EditConfirmation.SignOut)
            }

            EditInstanceIntent.RemoveRequested -> {
                actions.teardown.ask(EditConfirmation.Remove)
            }

            EditInstanceIntent.ConfirmationAccepted -> {
                actions.teardown.accept()
            }

            EditInstanceIntent.ConfirmationDismissed -> {
                mutableState.update { it.copy(confirmation = null) }
            }

            EditInstanceIntent.MessageDismissed -> {
                mutableState.update { it.copy(error = null, deviceAuthIssue = null, serverKeyOutcome = null) }
            }

            else -> {
                // Field intents were handled by fieldIntent.
            }
        }
    }

    private fun addressChanged(text: String) {
        mutableState.update { current ->
            val parsed = useCases.validateAddress(text)
            val normalized = (parsed as? BaseUriResult.Valid)?.uri?.value
            current
                .copy(
                    address = text,
                    addressIssue = (parsed as? BaseUriResult.Invalid)?.error?.toIssue(),
                    probe = ProbeStatus.Idle,
                    probeRefusal = null,
                    addressChangeClearsKey =
                        current.mode == EditMode.Edit && normalized != null &&
                            normalized != actions.session.savedAddress,
                ).revalidated()
        }
    }

    private fun online(action: () -> Unit) {
        if (connectivity.isOnline.value) {
            action()
        } else {
            mutableState.update {
                it.copy(
                    error = ErrorUi(ErrorKey.Unreachable),
                )
            }
        }
    }
}

/** Whether Save is enabled: a name, a valid address and, when the address is new, an accepted probe. */
private fun EditInstanceState.revalidated(): EditInstanceState {
    val addressOk = addressIssue == null && address.isNotBlank()
    val needsProbe = mode == EditMode.Add || addressChangeClearsKey
    return copy(canSave = name.isNotBlank() && addressOk && (!needsProbe || probe == ProbeStatus.Accepted))
}

/** What the parts of the edit screen share: the loaded factory, the probe report and the state. */
internal class EditInstanceSession(
    private val useCases: EditInstanceUseCases,
    private val state: MutableStateFlow<EditInstanceState>,
) {
    var loaded: Instance? = null
    var report: HealthReport? = null
    val savedAddress: String? get() = loaded?.baseUri?.value

    fun show(instance: Instance) {
        loaded = instance
        state.update {
            it
                .copy(
                    mode = EditMode.Edit,
                    instanceId = instance.id.value,
                    name = instance.displayName,
                    address = instance.baseUri.value,
                    addressIssue = null,
                    addressChangeClearsKey = false,
                    status = instance.status.toUi(),
                    expiry = useCases.expiry(instance.keyExpiresAt).toUi(),
                    exposure = instance.exposure,
                    defaultCompanyId = instance.defaultCompanyId?.value,
                    pins = instance.pins.map { pin -> pin.value },
                    pinningEnabled = instance.pins.isNotEmpty(),
                    isBusy = false,
                ).revalidated()
        }
    }

    suspend fun loadAccess(id: InstanceId) {
        if (loaded?.status != InstanceStatus.SignedIn) return
        when (val access = useCases.loadAccess(id)) {
            is DomainResult.Success -> {
                state.update {
                    it.copy(
                        companies = access.value.companies.map { c -> c.toUi() },
                        profileName = access.value.profile.name,
                        profileEmail = access.value.profile.email,
                    )
                }
            }

            is DomainResult.Failure -> {
                val refreshed = useCases.get(id)
                state.update {
                    it.copy(
                        error = access.error.toErrorUi(),
                        status = refreshed?.status?.toUi() ?: it.status,
                    )
                }
            }
        }
    }
}

/** The pieces of the edit screen's behaviour. */
internal class EditInstanceActions(
    private val useCases: EditInstanceUseCases,
    private val state: MutableStateFlow<EditInstanceState>,
    private val scope: CoroutineScope,
) {
    val session = EditInstanceSession(useCases, state)
    val fields = EditInstanceFields(useCases, state, scope, session)
    val saving = EditInstanceSaving(useCases, state, scope, session)
    val pairing = EditInstancePairing(useCases, state, scope, session)
    val teardown = EditInstanceTeardown(useCases, state, scope, session)

    fun load(id: InstanceId) {
        state.update { it.copy(mode = EditMode.Edit, instanceId = id.value, isBusy = true) }
        scope.launch {
            val instance = useCases.get(id)
            if (instance == null) {
                state.update { it.copy(isBusy = false, error = ErrorUi(ErrorKey.NotFound)) }
            } else {
                session.show(instance)
                session.loadAccess(id)
            }
        }
    }
}

/** Probe and certificate pins. */
internal class EditInstanceFields(
    private val useCases: EditInstanceUseCases,
    private val state: MutableStateFlow<EditInstanceState>,
    private val scope: CoroutineScope,
    private val session: EditInstanceSession,
) {
    fun probe() {
        val parsed = useCases.validateAddress(state.value.address) as? BaseUriResult.Valid ?: return
        if (state.value.probe == ProbeStatus.Probing) return
        state.update { it.copy(probe = ProbeStatus.Probing, error = null, probeRefusal = null) }
        scope.launch {
            val result = useCases.probe(parsed.uri)
            session.report = (result as? ProbeResult.Accepted)?.report
            state.update { it.withProbe(result).revalidated() }
        }
    }

    private fun EditInstanceState.withProbe(result: ProbeResult): EditInstanceState =
        when (result) {
            is ProbeResult.Accepted -> {
                copy(
                    probe = ProbeStatus.Accepted,
                    serverVersion = result.report.serverVersion,
                    exposure = result.report.exposure,
                )
            }

            is ProbeResult.Refused -> {
                copy(probe = ProbeStatus.Refused, probeRefusal = result.reason, exposure = result.report.exposure)
            }

            is ProbeResult.Failed -> {
                copy(probe = ProbeStatus.Failed, error = result.error.toErrorUi())
            }
        }

    fun addPin(text: String) {
        val pin = SpkiPin.parse(text)
        state.update {
            when {
                pin == null -> {
                    it.copy(pinInvalid = true)
                }

                pin.value in it.pins -> {
                    it.copy(pinInvalid = false, pinEntryOpen = false)
                }

                else -> {
                    it.copy(
                        pins = it.pins + pin.value,
                        pinningEnabled = true,
                        pinEntryOpen = false,
                        pinInvalid = false,
                    )
                }
            }
        }
    }

    fun removePin(pin: String) {
        state.update {
            val remaining = it.pins - pin
            it.copy(pins = remaining, pinningEnabled = remaining.isNotEmpty() || it.pinEntryOpen)
        }
    }

    fun togglePinning(enabled: Boolean) {
        state.update {
            if (enabled) {
                it.copy(pinningEnabled = true, pinEntryOpen = it.pins.isEmpty())
            } else {
                it.copy(pinningEnabled = false, pins = emptyList(), pinEntryOpen = false, pinInvalid = false)
            }
        }
    }
}

/** Save: adds the factory, or edits it. */
internal class EditInstanceSaving(
    private val useCases: EditInstanceUseCases,
    private val state: MutableStateFlow<EditInstanceState>,
    private val scope: CoroutineScope,
    private val session: EditInstanceSession,
) {
    fun save() {
        val current = state.value
        if (!current.canSave || current.isBusy) return
        state.update { it.copy(isBusy = true, error = null, nameIssue = null) }
        scope.launch {
            val id = current.instanceId?.let { InstanceId(it) }
            if (id == null) add(current) else update(id, current)
        }
    }

    private suspend fun add(current: EditInstanceState) {
        val probed = session.report
        if (probed == null) {
            state.update { it.copy(isBusy = false) }
            return
        }
        when (val result = useCases.add(current.name, current.address, probed)) {
            is AddInstanceResult.Added -> {
                saved(result.instance)
            }

            is AddInstanceResult.InvalidName -> {
                state.update { it.copy(isBusy = false, nameIssue = result.issue) }
            }

            is AddInstanceResult.InvalidAddress -> {
                state.update { it.copy(isBusy = false, addressIssue = result.error.toIssue()).revalidated() }
            }

            is AddInstanceResult.Refused -> {
                state.update { it.copy(isBusy = false, probe = ProbeStatus.Refused, probeRefusal = result.reason) }
            }
        }
    }

    private suspend fun update(
        id: InstanceId,
        current: EditInstanceState,
    ) {
        val edit =
            InstanceEdit(
                displayName = current.name,
                address = current.address,
                defaultCompanyId = current.defaultCompanyId?.let { CompanyId(it) },
                pins = current.pins.mapNotNull { SpkiPin.parse(it) },
            )
        when (val result = useCases.update(id, edit)) {
            is UpdateInstanceResult.Updated -> {
                saved(result.instance)
            }

            is UpdateInstanceResult.InvalidName -> {
                state.update { it.copy(isBusy = false, nameIssue = result.issue) }
            }

            is UpdateInstanceResult.InvalidAddress -> {
                state.update { it.copy(isBusy = false, addressIssue = result.error.toIssue()).revalidated() }
            }

            UpdateInstanceResult.NotFound -> {
                state.update { it.copy(isBusy = false, error = ErrorUi(ErrorKey.NotFound)) }
            }
        }
    }

    private fun saved(instance: Instance) {
        session.show(instance)
        state.update { it.copy(saved = true, probe = ProbeStatus.Idle) }
    }
}

/** Re-pair. */
internal class EditInstancePairing(
    private val useCases: EditInstanceUseCases,
    private val state: MutableStateFlow<EditInstanceState>,
    private val scope: CoroutineScope,
    private val session: EditInstanceSession,
) {
    private var pairJob: Job? = null

    fun pair() {
        val id = state.value.instanceId?.let { InstanceId(it) } ?: return
        if (state.value.pairing == PairingStatus.Waiting) return
        state.update { it.copy(pairing = PairingStatus.Waiting, error = null) }
        pairJob =
            scope.launch {
                val result = useCases.pair(id, state.value.defaultCompanyId?.let { CompanyId(it) })
                if (result is SignInResult.SignedIn) {
                    useCases.get(id)?.let { session.show(it) }
                    state.update { it.copy(pairing = PairingStatus.Idle) }
                    session.loadAccess(id)
                } else {
                    state.update { it.withSignInFailure(result) }
                }
            }
    }

    private fun EditInstanceState.withSignInFailure(result: SignInResult): EditInstanceState =
        when (result) {
            SignInResult.Cancelled -> copy(pairing = PairingStatus.Cancelled)
            SignInResult.Expired -> copy(pairing = PairingStatus.Expired)
            is SignInResult.Failed -> copy(pairing = PairingStatus.Failed, error = result.error.toErrorUi())
            else -> copy(pairing = PairingStatus.Failed, error = ErrorUi(ErrorKey.NotFound))
        }

    fun cancel() {
        val job = pairJob ?: return
        pairJob = null
        job.cancel()
        state.update { if (it.pairing == PairingStatus.Waiting) it.copy(pairing = PairingStatus.Cancelled) else it }
    }
}

/** Sign out and remove, each behind a confirmation. */
internal class EditInstanceTeardown(
    private val useCases: EditInstanceUseCases,
    private val state: MutableStateFlow<EditInstanceState>,
    private val scope: CoroutineScope,
    private val session: EditInstanceSession,
) {
    fun ask(confirmation: EditConfirmation) {
        if (state.value.instanceId == null) return
        state.update { it.copy(confirmation = confirmation, deviceAuthIssue = null) }
    }

    fun accept() {
        val confirmation = state.value.confirmation ?: return
        val id = state.value.instanceId?.let { InstanceId(it) } ?: return
        if (state.value.isBusy) return
        state.update {
            it.copy(
                isBusy = true,
                confirmation =
                    if (confirmation ==
                        EditConfirmation.Remove
                    ) {
                        it.confirmation
                    } else {
                        null
                    },
            )
        }
        scope.launch {
            when (confirmation) {
                EditConfirmation.SignOut -> signOut(id)
                EditConfirmation.Remove -> remove(id)
            }
        }
    }

    private suspend fun signOut(id: InstanceId) {
        val result = useCases.signOut(id)
        useCases.get(id)?.let { session.show(it) }
        val outcome = (result as? SignOutResult.SignedOut)?.serverKey?.toUi()
        state.update { it.copy(isBusy = false, serverKeyOutcome = outcome, companies = emptyList()) }
    }

    private suspend fun remove(id: InstanceId) {
        when (val result = useCases.remove(id)) {
            is RemoveInstanceResult.Removed -> {
                state.update {
                    it.copy(
                        isBusy = false,
                        confirmation = null,
                        removed = true,
                        serverKeyOutcome = result.serverKey.toUi(),
                    )
                }
            }

            is RemoveInstanceResult.NotAuthorized -> {
                state.update { it.copy(isBusy = false, deviceAuthIssue = result.result.toIssue()) }
            }

            RemoveInstanceResult.NotFound -> {
                state.update { it.copy(isBusy = false, confirmation = null, removed = true) }
            }
        }
    }
}
