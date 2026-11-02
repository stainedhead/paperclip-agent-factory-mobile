package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.ValidateInstanceAddress
import com.stainedhead.paperclipmobile.application.auth.LoadInstanceAccess
import com.stainedhead.paperclipmobile.application.auth.PairInstance
import com.stainedhead.paperclipmobile.application.auth.SaveManualBoardKey
import com.stainedhead.paperclipmobile.application.auth.SignInResult
import com.stainedhead.paperclipmobile.application.instance.AddInstance
import com.stainedhead.paperclipmobile.application.instance.AddInstanceResult
import com.stainedhead.paperclipmobile.application.instance.GetInstance
import com.stainedhead.paperclipmobile.application.instance.InstanceNameIssue
import com.stainedhead.paperclipmobile.application.instance.ProbeInstance
import com.stainedhead.paperclipmobile.application.instance.ProbeResult
import com.stainedhead.paperclipmobile.application.instance.SwitchInstance
import com.stainedhead.paperclipmobile.application.instance.UpdateInstance
import com.stainedhead.paperclipmobile.application.instance.UpdateInstanceResult
import com.stainedhead.paperclipmobile.application.instance.toEdit
import com.stainedhead.paperclipmobile.application.settings.AppSettingsUpdate
import com.stainedhead.paperclipmobile.application.settings.UpdateAppSettings
import com.stainedhead.paperclipmobile.domain.BaseUriResult
import com.stainedhead.paperclipmobile.domain.CompanyId
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Exposure
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.ProbeRefusal
import com.stainedhead.paperclipmobile.domain.Secret
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The three steps of PRD FR-009 (Address, Pair, Secure) and the end of the flow. */
enum class FirstRunStep {
    Address,
    Pair,
    Secure,
    Done,
}

enum class ProbeStatus {
    Idle,
    Probing,
    Accepted,
    Refused,
    Failed,
}

enum class PairingStatus {
    Idle,
    Waiting,
    Cancelled,
    Expired,
    Failed,
}

data class FirstRunState(
    val step: FirstRunStep = FirstRunStep.Address,
    val address: String = "",
    val normalizedAddress: String? = null,
    val issue: AddressIssue? = null,
    val canContinue: Boolean = false,
    val name: String = "",
    val nameIssue: InstanceNameIssue? = null,
    val probe: ProbeStatus = ProbeStatus.Idle,
    val probeRefusal: ProbeRefusal? = null,
    val serverVersion: String? = null,
    val exposure: Exposure? = null,
    val pairing: PairingStatus = PairingStatus.Idle,
    val companies: List<CompanyUi> = emptyList(),
    val defaultCompanyId: String? = null,
    val appLockEnabled: Boolean = true,
    val isBusy: Boolean = false,
    val error: ErrorUi? = null,
    val isDemo: Boolean = false,
)

sealed interface FirstRunIntent {
    data class AddressChanged(
        val text: String,
    ) : FirstRunIntent

    data class NameChanged(
        val text: String,
    ) : FirstRunIntent

    /** Checks the address with `GET /api/health` (PRD FR-005). */
    data object ProbeRequested : FirstRunIntent

    /** Saves the factory after an accepted probe and moves to pairing. */
    data object ContinueRequested : FirstRunIntent

    data object PairRequested : FirstRunIntent

    data object PairCancelled : FirstRunIntent

    data class ManualKeySubmitted(
        val key: String,
    ) : FirstRunIntent

    data class DefaultCompanyChosen(
        val companyId: String,
    ) : FirstRunIntent

    data class AppLockChosen(
        val enabled: Boolean,
    ) : FirstRunIntent

    data object FinishRequested : FirstRunIntent

    data object BackRequested : FirstRunIntent

    /** Runs the app on the shared fakes with no network (PRD FR-009). */
    data object DemoSelected : FirstRunIntent

    data object ErrorDismissed : FirstRunIntent
}

/** What the first-run flow needs. */
data class FirstRunUseCases(
    val validateAddress: ValidateInstanceAddress,
    val probe: ProbeInstance,
    val add: AddInstance,
    val update: UpdateInstance,
    val get: GetInstance,
    val pair: PairInstance,
    val saveKey: SaveManualBoardKey,
    val loadAccess: LoadInstanceAccess,
    val updateSettings: UpdateAppSettings,
    val switchInstance: SwitchInstance,
)

/** State holder for the first-run setup flow (screen 15): address, pair, secure. */
class FirstRunStateHolder(
    private val useCases: FirstRunUseCases,
    scope: CoroutineScope,
) {
    private val scope = childScope(scope)
    private val mutableState = MutableStateFlow(FirstRunState())
    private val actions = FirstRunActions(useCases, mutableState, this.scope)
    val state: StateFlow<FirstRunState> = mutableState.asStateFlow()

    fun onIntent(intent: FirstRunIntent) {
        when (intent) {
            is FirstRunIntent.AddressChanged -> {
                addressChanged(intent.text)
            }

            is FirstRunIntent.NameChanged -> {
                nameChanged(intent.text)
            }

            FirstRunIntent.ProbeRequested -> {
                actions.probe()
            }

            FirstRunIntent.ContinueRequested -> {
                actions.save()
            }

            FirstRunIntent.PairRequested -> {
                actions.pair()
            }

            FirstRunIntent.PairCancelled -> {
                actions.cancelPairing()
            }

            is FirstRunIntent.ManualKeySubmitted -> {
                actions.manualKey(intent.key)
            }

            is FirstRunIntent.DefaultCompanyChosen -> {
                mutableState.update {
                    it.copy(
                        defaultCompanyId = intent.companyId,
                    )
                }
            }

            is FirstRunIntent.AppLockChosen -> {
                mutableState.update { it.copy(appLockEnabled = intent.enabled) }
            }

            FirstRunIntent.FinishRequested -> {
                actions.finish()
            }

            FirstRunIntent.BackRequested -> {
                actions.back()
            }

            FirstRunIntent.DemoSelected -> {
                mutableState.update { it.copy(step = FirstRunStep.Done, isDemo = true) }
            }

            FirstRunIntent.ErrorDismissed -> {
                mutableState.update { it.copy(error = null) }
            }
        }
    }

    /** Cancels any work in flight. The platform calls this when the screen goes away. */
    fun close() {
        actions.cancelPairing()
        scope.cancel()
    }

    private fun addressChanged(text: String) {
        actions.addressChanged()
        mutableState.update { it.withAddress(text, useCases.validateAddress(text)).copy(probe = ProbeStatus.Idle) }
    }

    private fun nameChanged(text: String) {
        mutableState.update { it.copy(name = text, nameIssue = null) }
    }

    private fun FirstRunState.withAddress(
        text: String,
        result: BaseUriResult,
    ): FirstRunState =
        when (result) {
            is BaseUriResult.Valid -> {
                copy(
                    address = text,
                    normalizedAddress = result.uri.value,
                    issue = null,
                    canContinue = true,
                    probeRefusal = null,
                    error = null,
                )
            }

            is BaseUriResult.Invalid -> {
                copy(
                    address = text,
                    normalizedAddress = null,
                    issue = result.error.toIssue(),
                    canContinue = false,
                    probeRefusal = null,
                    error = null,
                )
            }
        }
}

/** What the steps of the first-run flow share: the factory added so far and what its probe returned. */
internal class FirstRunSession {
    var instanceId: InstanceId? = null

    /** The accepted probe of the address that was probed; null when no probe is accepted for the current address. */
    var probed: ProbeResult.Accepted? = null
    var probeJob: Job? = null
}

/** The asynchronous steps of the first-run flow. */
internal class FirstRunActions(
    private val useCases: FirstRunUseCases,
    private val state: MutableStateFlow<FirstRunState>,
    private val scope: CoroutineScope,
) {
    private val session = FirstRunSession()
    private val saving = FirstRunSaving(useCases, state, scope, session)
    private val pairing = FirstRunPairing(useCases, state, scope, session)

    fun save() = saving.save()

    fun pair() = pairing.pair()

    fun cancelPairing() = pairing.cancel()

    fun manualKey(key: String) = pairing.manualKey(key)

    fun probe() {
        val address =
            state.value.takeIf { it.canContinue && it.probe != ProbeStatus.Probing }?.normalizedAddress ?: return
        val uri = (useCases.validateAddress(address) as? BaseUriResult.Valid)?.uri ?: return
        state.update { it.copy(probe = ProbeStatus.Probing, error = null, probeRefusal = null) }
        session.probeJob?.cancel()
        session.probeJob =
            scope.launch {
                val result = useCases.probe(uri)
                if (result.baseUri.value != state.value.normalizedAddress) return@launch
                session.probed = result as? ProbeResult.Accepted
                state.update { it.withProbe(result) }
            }
    }

    /** The address was edited: a probe still in flight is for another address, so it is cancelled and forgotten. */
    fun addressChanged() {
        session.probeJob?.cancel()
        session.probed = null
    }

    private fun FirstRunState.withProbe(result: ProbeResult): FirstRunState =
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

    fun finish() {
        val id = session.instanceId ?: return
        val current = state.value
        if (current.step != FirstRunStep.Secure || current.isBusy) return
        state.update { it.copy(isBusy = true) }
        scope.launch {
            useCases.updateSettings(AppSettingsUpdate(appLockEnabled = current.appLockEnabled))
            current.defaultCompanyId?.let { company ->
                useCases.get(id)?.let { useCases.update(id, it.toEdit().copy(defaultCompanyId = CompanyId(company))) }
            }
            useCases.switchInstance(id)
            state.update { it.copy(step = FirstRunStep.Done, isBusy = false) }
        }
    }

    fun back() {
        cancelPairing()
        session.probeJob?.cancel()
        state.update {
            if (it.step ==
                FirstRunStep.Pair
            ) {
                it.copy(step = FirstRunStep.Address, pairing = PairingStatus.Idle, error = null)
            } else {
                it
            }
        }
    }
}

/** Saves the factory once its probe is accepted (adds it, or edits it when the person came back). */
internal class FirstRunSaving(
    private val useCases: FirstRunUseCases,
    private val state: MutableStateFlow<FirstRunState>,
    private val scope: CoroutineScope,
    private val session: FirstRunSession,
) {
    fun save() {
        val current = state.value
        val probed = session.probed
        if (current.probe != ProbeStatus.Accepted || probed == null || current.isBusy) return
        val address = current.normalizedAddress ?: return
        val name = current.name.ifBlank { address.substringAfter("://").substringBefore('/').substringBefore(':') }
        state.update { it.copy(isBusy = true, error = null, nameIssue = null) }
        scope.launch {
            val existing = session.instanceId
            val saved =
                if (existing ==
                    null
                ) {
                    add(name, address, probed)
                } else {
                    update(existing, name, address, probed)
                }
            state.update { if (saved) it.copy(step = FirstRunStep.Pair, isBusy = false) else it.copy(isBusy = false) }
        }
    }

    private suspend fun add(
        name: String,
        address: String,
        probed: ProbeResult.Accepted,
    ): Boolean =
        when (val result = useCases.add(name, address, probed)) {
            is AddInstanceResult.Added -> {
                session.instanceId = result.instance.id
                true
            }

            is AddInstanceResult.InvalidName -> {
                fail { it.copy(nameIssue = result.issue) }
            }

            is AddInstanceResult.InvalidAddress -> {
                fail { it.copy(issue = result.error.toIssue(), canContinue = false) }
            }

            is AddInstanceResult.Refused -> {
                fail { it.copy(probe = ProbeStatus.Refused, probeRefusal = result.reason) }
            }

            AddInstanceResult.ProbeRequired -> {
                fail { it.copy(probe = ProbeStatus.Idle) }
            }
        }

    private suspend fun update(
        id: InstanceId,
        name: String,
        address: String,
        probed: ProbeResult.Accepted,
    ): Boolean {
        val current = useCases.get(id) ?: return fail { it.copy(error = ErrorUi(ErrorKey.NotFound)) }
        val edit = current.toEdit().copy(displayName = name, address = address, probed = probed)
        return when (val result = useCases.update(id, edit)) {
            is UpdateInstanceResult.Updated -> {
                true
            }

            is UpdateInstanceResult.InvalidName -> {
                fail { it.copy(nameIssue = result.issue) }
            }

            is UpdateInstanceResult.InvalidAddress -> {
                fail {
                    it.copy(
                        issue = result.error.toIssue(),
                        canContinue = false,
                    )
                }
            }

            UpdateInstanceResult.NotFound -> {
                fail { it.copy(error = ErrorUi(ErrorKey.NotFound)) }
            }

            UpdateInstanceResult.ProbeRequired -> {
                fail { it.copy(probe = ProbeStatus.Idle) }
            }
        }
    }

    private fun fail(change: (FirstRunState) -> FirstRunState): Boolean {
        state.update(change)
        return false
    }
}

/** Pairing with the factory, by browser approval or by a pasted key, then reading its companies. */
internal class FirstRunPairing(
    private val useCases: FirstRunUseCases,
    private val state: MutableStateFlow<FirstRunState>,
    private val scope: CoroutineScope,
    private val session: FirstRunSession,
) {
    private var pairJob: Job? = null

    fun pair() {
        val id = session.instanceId ?: return
        if (state.value.pairing == PairingStatus.Waiting) return
        state.update { it.copy(pairing = PairingStatus.Waiting, error = null) }
        pairJob =
            scope.launch {
                val companyId = state.value.defaultCompanyId?.let { CompanyId(it) }
                signedIn(id, useCases.pair(id, companyId))
            }
    }

    fun cancel() {
        val job = pairJob ?: return
        pairJob = null
        job.cancel()
        state.update { if (it.pairing == PairingStatus.Waiting) it.copy(pairing = PairingStatus.Cancelled) else it }
    }

    fun manualKey(key: String) {
        val id = session.instanceId ?: return
        if (state.value.isBusy) return
        state.update { it.copy(isBusy = true, error = null) }
        scope.launch { signedIn(id, useCases.saveKey(id, Secret.of(key.trim()))) }
    }

    private suspend fun signedIn(
        id: InstanceId,
        result: SignInResult,
    ) {
        if (result is SignInResult.SignedIn) {
            loadCompanies(id)
        } else {
            state.update { it.withSignInFailure(result) }
        }
    }

    private fun FirstRunState.withSignInFailure(result: SignInResult): FirstRunState =
        result.toFailure()?.let { copy(pairing = it.pairing, isBusy = false, error = it.error) } ?: this

    private suspend fun loadCompanies(id: InstanceId) {
        when (val access = useCases.loadAccess(id)) {
            is DomainResult.Failure -> {
                state.update {
                    it.copy(
                        pairing = PairingStatus.Failed,
                        isBusy = false,
                        error = access.error.toErrorUi(),
                    )
                }
            }

            is DomainResult.Success -> {
                val companies = access.value.companies.map { it.toUi() }
                state.update {
                    it.copy(
                        step = FirstRunStep.Secure,
                        pairing = PairingStatus.Idle,
                        isBusy = false,
                        companies = companies,
                        defaultCompanyId =
                            it.defaultCompanyId?.takeIf { chosen -> companies.any { c -> c.id == chosen } }
                                ?: companies.singleOrNull()?.id,
                    )
                }
            }
        }
    }
}
