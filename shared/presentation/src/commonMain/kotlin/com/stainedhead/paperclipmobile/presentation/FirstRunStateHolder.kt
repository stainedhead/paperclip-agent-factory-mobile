package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.ValidateInstanceAddress
import com.stainedhead.paperclipmobile.domain.BaseUriError
import com.stainedhead.paperclipmobile.domain.BaseUriResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What is wrong with the address, as a key the platform UI turns into a localised message. */
enum class AddressIssue {
    MissingScheme,
    MustUseHttps,
    MissingHost,
    InvalidHost,
    InvalidPort,
    RemoveCredentials,
    RemoveQueryOrFragment,
}

data class FirstRunState(
    val address: String = "",
    val normalizedAddress: String? = null,
    val issue: AddressIssue? = null,
    val canContinue: Boolean = false,
)

sealed interface FirstRunIntent {
    data class AddressChanged(
        val text: String,
    ) : FirstRunIntent
}

/** State holder for the first-run setup screen (screen 15): the address step. */
class FirstRunStateHolder(
    private val validateAddress: ValidateInstanceAddress,
) {
    private val mutableState = MutableStateFlow(FirstRunState())
    val state: StateFlow<FirstRunState> = mutableState.asStateFlow()

    fun onIntent(intent: FirstRunIntent) {
        when (intent) {
            is FirstRunIntent.AddressChanged -> mutableState.value = stateFor(intent.text)
        }
    }

    private fun stateFor(text: String): FirstRunState =
        when (val result = validateAddress(text)) {
            is BaseUriResult.Valid -> FirstRunState(text, result.uri.value, issue = null, canContinue = true)
            is BaseUriResult.Invalid -> FirstRunState(text, issue = result.error.toIssue())
        }

    private fun BaseUriError.toIssue(): AddressIssue? =
        when (this) {
            BaseUriError.Empty -> null
            BaseUriError.MissingScheme -> AddressIssue.MissingScheme
            BaseUriError.UnsupportedScheme, BaseUriError.InsecureHttp -> AddressIssue.MustUseHttps
            BaseUriError.MissingHost -> AddressIssue.MissingHost
            BaseUriError.InvalidHost -> AddressIssue.InvalidHost
            BaseUriError.InvalidPort -> AddressIssue.InvalidPort
            BaseUriError.UserInfoNotAllowed -> AddressIssue.RemoveCredentials
            BaseUriError.QueryOrFragmentNotAllowed -> AddressIssue.RemoveQueryOrFragment
        }
}
