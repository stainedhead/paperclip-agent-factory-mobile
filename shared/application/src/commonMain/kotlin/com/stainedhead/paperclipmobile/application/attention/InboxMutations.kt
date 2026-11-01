package com.stainedhead.paperclipmobile.application.attention

import com.stainedhead.paperclipmobile.application.InvalidInput
import com.stainedhead.paperclipmobile.application.MutationResult
import com.stainedhead.paperclipmobile.application.toMutation
import com.stainedhead.paperclipmobile.domain.Action
import com.stainedhead.paperclipmobile.domain.AttentionItem
import com.stainedhead.paperclipmobile.domain.CompanyContext
import com.stainedhead.paperclipmobile.domain.DecideBy
import com.stainedhead.paperclipmobile.domain.Dismissal
import com.stainedhead.paperclipmobile.domain.Permissions
import com.stainedhead.paperclipmobile.domain.SnoozePreset
import com.stainedhead.paperclipmobile.domain.SnoozeTimes
import com.stainedhead.paperclipmobile.domain.port.AttentionGateway
import com.stainedhead.paperclipmobile.domain.port.Clock
import com.stainedhead.paperclipmobile.domain.port.DeviceTimeZone
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Hides an item until a time chosen from the presets (PRD FR-034, ADR-020). */
class SnoozeItem(
    private val gateway: AttentionGateway,
    private val clock: Clock,
    private val timeZone: DeviceTimeZone,
) {
    suspend operator fun invoke(
        context: CompanyContext,
        item: AttentionItem,
        preset: SnoozePreset,
    ): MutationResult<Dismissal> {
        if (!Permissions.allows(context.role, Action.SnoozeItem)) return MutationResult.Denied
        val now = clock.now()
        val zone = timeZone.current()
        if (!SnoozeTimes.isAvailable(preset, now, zone)) return MutationResult.Invalid(InvalidInput.SnoozeNotInFuture)
        return gateway.snooze(context.scope, item.dismissalKey, SnoozeTimes.resolve(preset, now, zone)).toMutation()
    }
}

/** Dismisses an item until it has new activity (PRD FR-034). */
class DismissItem(
    private val gateway: AttentionGateway,
) {
    suspend operator fun invoke(
        context: CompanyContext,
        item: AttentionItem,
    ): MutationResult<Dismissal> {
        if (!Permissions.allows(context.role, Action.DismissItem)) return MutationResult.Denied
        return gateway.dismiss(context.scope, item.dismissalKey).toMutation()
    }
}

/** Brings back a dismissed or snoozed item, within five seconds of the swipe (PRD FR-034). */
class UndoDismiss(
    private val gateway: AttentionGateway,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        context: CompanyContext,
        item: AttentionItem,
        dismissedAt: Instant,
    ): MutationResult<Unit> {
        if (!Permissions.allows(context.role, Action.UndoDismiss)) return MutationResult.Denied
        if (clock.now() - dismissedAt > UNDO_WINDOW) return MutationResult.Invalid(InvalidInput.UndoWindowClosed)
        return gateway.restore(context.scope, item.dismissalKey).toMutation()
    }

    companion object {
        val UNDO_WINDOW = 5.seconds
    }
}

/** Sets or clears when a person wants to decide an item (PRD FR-034, research triage). */
class SetDecideBy(
    private val gateway: AttentionGateway,
) {
    suspend operator fun invoke(
        context: CompanyContext,
        item: AttentionItem,
        decideBy: DecideBy,
    ): MutationResult<Unit> {
        if (!Permissions.allows(context.role, Action.SetDecideBy)) return MutationResult.Denied
        return gateway.setDecideBy(context.scope, item.sourceKind, item.subject.id, decideBy).toMutation()
    }
}

/** The snooze presets that still make sense now, for example no "tonight" after 20:00 (ADR-020). */
class AvailableSnoozePresets(
    private val clock: Clock,
    private val timeZone: DeviceTimeZone,
) {
    operator fun invoke(): List<SnoozePreset> =
        SnoozePreset.entries.filter { SnoozeTimes.isAvailable(it, clock.now(), timeZone.current()) }
}
