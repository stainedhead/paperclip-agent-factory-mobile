package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.domain.Action
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.domain.Permissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The connection and the clock, as the screen sees them. */
internal class ScreenEnv(
    val online: () -> Boolean,
    val nowMillis: () -> Long,
)

/** What the Issue screen's helpers share: the model, the use cases and the rules for "may I act now". */
internal class IssueSession(
    val useCases: IssueUseCases,
    val context: ContextSource,
    val scope: CoroutineScope,
    val id: IssueId,
    val model: MutableStateFlow<IssueModel>,
    private val screen: ScreenEnv,
) {
    val companyScope: CompanyScope get() = context.scope

    fun online(): Boolean = screen.online()

    fun nowMillis(): Long = screen.nowMillis()

    /** The person's role allows [action], there is a connection, and no other change is in flight. */
    fun may(action: Action): Boolean {
        val role = model.value.role ?: return false
        return online() && !model.value.busy && Permissions.allows(role, action)
    }

    /** Marks a change as in flight so a second tap is ignored, runs it, and records its feedback. */
    internal fun <T> change(
        call: suspend () -> ActionResult<T>,
        apply: (IssueModel, T) -> IssueModel,
        reload: (() -> Unit)? = null,
    ) {
        model.update { it.copy(busy = true, feedback = null) }
        scope.launch {
            val result = call()
            model.update { current ->
                val applied = result.value?.let { apply(current, it) } ?: current
                applied.copy(busy = false, feedback = result.feedback)
            }
            if (result.value != null) reload?.invoke()
        }
    }
}
