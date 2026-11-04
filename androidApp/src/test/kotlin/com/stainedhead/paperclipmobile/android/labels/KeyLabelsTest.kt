package com.stainedhead.paperclipmobile.android.labels

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.application.instance.InstanceNameIssue
import com.stainedhead.paperclipmobile.domain.Exposure
import com.stainedhead.paperclipmobile.domain.ProbeRefusal
import com.stainedhead.paperclipmobile.presentation.ActionsDisabledReason
import com.stainedhead.paperclipmobile.presentation.AddressIssue
import com.stainedhead.paperclipmobile.presentation.DeviceAuthIssue
import com.stainedhead.paperclipmobile.presentation.ErrorKey
import com.stainedhead.paperclipmobile.presentation.FactoryStatusUi
import com.stainedhead.paperclipmobile.presentation.PairingStatus
import com.stainedhead.paperclipmobile.presentation.ProbeStatus
import com.stainedhead.paperclipmobile.presentation.RoleUi
import com.stainedhead.paperclipmobile.presentation.SeverityUi
import com.stainedhead.paperclipmobile.presentation.StartupNotice
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AND-016, the Android twin of the String Catalog test: every entry of every key enum the shared core hands the app has
 * its own non-blank string, named `<namespace>_<entry>`. A new entry fails to compile (the mapping is an exhaustive
 * `when`); a mapping that points at another entry's text fails here.
 */
@RunWith(AndroidJUnit4::class)
class KeyLabelsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun <K : Enum<K>> check(
        namespace: String,
        entries: List<K>,
        resource: (K) -> Int,
    ) {
        (entries.isNotEmpty()) shouldBe true
        for (entry in entries) {
            val id = resource(entry)
            val name = context.resources.getResourceEntryName(id)
            name shouldBe "${namespace}_${entry.name.toSnakeCase()}"
            context.getString(id).shouldNotBeBlank()
        }
    }

    @Test
    fun everyErrorKeyHasMessageText() = check("error", ErrorKey.entries, ErrorKey::stringRes)

    @Test
    fun everyActionsDisabledReasonHasText() =
        check("disabled", ActionsDisabledReason.entries, ActionsDisabledReason::stringRes)

    @Test
    fun everyAddressIssueHasText() = check("address", AddressIssue.entries, AddressIssue::stringRes)

    @Test
    fun everyStartupNoticeHasText() = check("startup", StartupNotice.entries, StartupNotice::stringRes)

    @Test
    fun everyDeviceAuthIssueHasText() = check("device_auth", DeviceAuthIssue.entries, DeviceAuthIssue::stringRes)

    @Test
    fun everySeverityHasText() = check("severity", SeverityUi.entries, SeverityUi::stringRes)

    @Test
    fun everyProbeStatusHasText() = check("probe", ProbeStatus.entries, ProbeStatus::stringRes)

    @Test
    fun everyProbeRefusalHasText() = check("refusal", ProbeRefusal.entries, ProbeRefusal::stringRes)

    @Test
    fun everyPairingStatusHasText() = check("pairing", PairingStatus.entries, PairingStatus::stringRes)

    @Test
    fun everyInstanceNameIssueHasText() = check("name_issue", InstanceNameIssue.entries, InstanceNameIssue::stringRes)

    @Test
    fun everyExposureHasText() = check("exposure", Exposure.entries, Exposure::stringRes)

    @Test
    fun everyRoleHasText() = check("role", RoleUi.entries, RoleUi::stringRes)

    @Test
    fun everyFactoryStatusHasText() = check("status", FactoryStatusUi.entries, FactoryStatusUi::stringRes)
}
