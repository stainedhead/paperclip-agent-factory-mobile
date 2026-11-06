package com.stainedhead.paperclipmobile.android.screens

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.AppType
import com.stainedhead.paperclipmobile.android.designsystem.CardGroup
import com.stainedhead.paperclipmobile.android.designsystem.CardSurface
import com.stainedhead.paperclipmobile.android.designsystem.HairlineDivider
import com.stainedhead.paperclipmobile.android.designsystem.Radius
import com.stainedhead.paperclipmobile.android.designsystem.RelativeAge
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.android.designsystem.compactText
import com.stainedhead.paperclipmobile.android.designsystem.rememberNowMillis
import com.stainedhead.paperclipmobile.android.labels.stringRes
import com.stainedhead.paperclipmobile.presentation.FileUi
import com.stainedhead.paperclipmobile.presentation.IssueState
import com.stainedhead.paperclipmobile.presentation.IssueTab

private const val STACK_FONT_SCALE = 1.3f
private const val MILLIS_PER_SECOND = 1_000L
private val TabPadding = 3.dp
private val LoadingHeight = Spacing.minimumTarget * 2

/** The text of a tab button: its name and, once the tab has been read, how many it holds ("Runs 3"). */
@Composable
private fun tabTitle(
    tab: IssueTab,
    state: IssueState,
): String {
    val name = stringResource(tab.stringRes())
    return when {
        tab == IssueTab.Runs && state.runsLoaded -> {
            stringResource(R.string.issue_tab_with_count, name, state.runs.size)
        }

        tab == IssueTab.Files && state.filesLoaded -> {
            stringResource(
                R.string.issue_tab_with_count,
                name,
                state.files.size,
            )
        }

        else -> {
            name
        }
    }
}

/**
 * Thread, Plan, Runs and Files as one segmented control, as in the mock. The tab that is open is selected for TalkBack,
 * not only filled; at large font sizes the four stack so no name is cut.
 */
@Composable
fun IssueTabs(
    state: IssueState,
    onSelect: (IssueTab) -> Unit,
) {
    val colors = AppTheme.colors
    val label = stringResource(R.string.issue_tabs_label)
    val tabs: @Composable (Modifier) -> Unit = { modifier ->
        for (tab in IssueTab.entries) {
            val selected = state.tab == tab
            Box(
                modifier
                    .heightIn(min = Spacing.minimumTarget)
                    .clip(RoundedCornerShape(Radius.button - 2.dp))
                    .background(if (selected) colors.surface else colors.muted)
                    .selectable(selected = selected, role = Role.Tab) { onSelect(tab) }
                    .testTag("issue-tab-${tab.name}"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    tabTitle(tab, state),
                    style =
                        AppType.secondary.copy(
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                    color = if (selected) colors.textPrimary else colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
    val container =
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.button))
            .background(colors.muted)
            .padding(TabPadding)
            .testTag("issue-tabs")
            .semantics { contentDescription = label }
    if (LocalDensity.current.fontScale >= STACK_FONT_SCALE) {
        Column(
            container,
            verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
        ) { tabs(Modifier.fillMaxWidth()) }
    } else {
        Row(container) { tabs(Modifier.weight(1f)) }
    }
}

@Composable
private fun TabNote(
    text: String,
    tag: String,
) {
    CardSurface(Modifier.testTag(tag)) { Text(text, style = AppType.body, color = AppTheme.colors.textSecondary) }
}

@Composable
private fun TabLoading(state: IssueState) {
    if (state.tabError != null) return
    val label = stringResource(R.string.issue_loading_tab)
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = LoadingHeight)
            .testTag("issue-tab-loading")
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { CircularProgressIndicator(color = AppTheme.colors.accent) }
}

/** The plan, read-only (PRD FR-061): its title, its text and how old it is. */
@Composable
fun PlanTab(state: IssueState) {
    val plan = state.plan
    if (plan == null) {
        TabNote(stringResource(R.string.issue_plan_empty), "issue-plan-empty")
        return
    }
    val colors = AppTheme.colors
    val now by rememberNowMillis()
    CardSurface(Modifier.testTag("issue-plan")) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
            plan.title?.let {
                Text(
                    it,
                    Modifier.semantics { heading() }.testTag("issue-plan-title"),
                    style = AppType.cardTitle,
                    color = colors.textPrimary,
                )
            }
            Text(plan.body, Modifier.testTag("issue-plan-body"), style = AppType.body, color = colors.textPrimary)
            plan.updatedAtEpochMillis?.let {
                val age = RelativeAge.of((now - it) / MILLIS_PER_SECOND)
                Text(
                    stringResource(R.string.common_updated_ago, age.compactText()),
                    Modifier.testTag("issue-plan-updated"),
                    style = AppType.secondary,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

/** The runs on this issue; a row opens the Run viewer. */
@Composable
fun RunsTab(
    state: IssueState,
    onOpenRun: (String) -> Unit,
) {
    when {
        !state.runsLoaded -> TabLoading(state)
        state.runs.isEmpty() -> TabNote(stringResource(R.string.issue_runs_empty), "issue-runs-empty")
        else -> RunRowsCard(state.runs, "issue-run", onOpenRun, Modifier.testTag("issue-runs"))
    }
}

/**
 * The files attached to the issue: name, type and size. They cannot be opened from the app yet: the shared core has no
 * port for the attachment's bytes (ADR-064), so the note says so instead of offering a button that cannot work.
 */
@Composable
fun FilesTab(state: IssueState) {
    when {
        !state.filesLoaded -> {
            TabLoading(state)
        }

        state.files.isEmpty() -> {
            TabNote(stringResource(R.string.issue_files_empty), "issue-files-empty")
        }

        else -> {
            Column(Modifier.testTag("issue-files"), verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
                CardGroup {
                    state.files.forEachIndexed { index, file ->
                        if (index > 0) HairlineDivider()
                        FileRow(file)
                    }
                }
                Text(
                    stringResource(R.string.issue_files_note),
                    Modifier.testTag("issue-files-note"),
                    style = AppType.secondary,
                    color = AppTheme.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun FileRow(file: FileUi) {
    val colors = AppTheme.colors
    val name = file.name ?: file.contentType
    val detail =
        stringResource(
            R.string.issue_file_line,
            file.contentType,
            Formatter.formatShortFileSize(LocalContext.current, file.byteSize),
        )
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.minimumTarget)
            .padding(vertical = Spacing.medium)
            .semantics(mergeDescendants = true) {}
            .testTag("issue-file-${file.id}"),
        verticalArrangement = Arrangement.spacedBy(Spacing.hairline),
    ) {
        Text(name, style = AppType.body, color = colors.textPrimary)
        Text(detail, style = AppType.secondary, color = colors.textSecondary)
    }
}
