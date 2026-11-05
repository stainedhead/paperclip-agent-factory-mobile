package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

private const val MINIMUM_TARGET_DP = 48
private const val ROUNDING_PX = 2f

/**
 * What a screen tells TalkBack, read from the merged semantics tree (T8.2): every control that can be clicked has a
 * name and a role and a touch target of at least 48 dp, and the reading order (tree order) follows the layout from the
 * top of the screen to the bottom. Returns one sentence per finding, empty for a clean screen.
 */
fun audit(
    root: SemanticsNode,
    density: Density,
): List<String> {
    val all = root.flatten()
    val minimum = with(density) { MINIMUM_TARGET_DP.dp.toPx() } - ROUNDING_PX
    val controls = all.filter { SemanticsActions.OnClick in it.config && !it.isHidden() && it.isPlaced() }
    val unnamed = controls.filter { it.name().isBlank() }.map { "no name: ${it.describe()}" }
    // A text field is announced as an edit box by the platform; it has no Role of its own.
    val roleless =
        controls
            .filter { it.config.getOrNull(SemanticsProperties.Role) == null && SemanticsActions.SetText !in it.config }
            .map { "no role: ${it.describe()}" }
    val small =
        controls
            .filter { it.touchBoundsInRoot.width < minimum || it.touchBoundsInRoot.height < minimum }
            .map {
                "target under 48 dp (${it.touchBoundsInRoot.width}x${it.touchBoundsInRoot.height} px): ${it.describe()}"
            }
    // A description on an edit box can replace the text TalkBack reads out; a field is named by its label (R-025).
    val describedFields =
        all
            .filter { SemanticsActions.SetText in it.config && SemanticsProperties.ContentDescription in it.config }
            .map { "text field named only by a description: ${it.describe()}" }
    return unnamed + roleless + small + describedFields + orderFindings(all)
}

private fun SemanticsNode.flatten(): List<SemanticsNode> = listOf(this) + children.flatMap { it.flatten() }

private fun SemanticsNode.isHidden() = SemanticsProperties.InvisibleToUser in config

/** Items of a lazy list or a scrolled-away chip row exist in the tree with no size until they are laid out. */
private fun SemanticsNode.isPlaced() = size.width > 0 && size.height > 0 && !touchBoundsInRoot.isEmpty

/** The text TalkBack would read for this node: its own description, else its text. */
fun SemanticsNode.name(): String =
    config
        .getOrNull(SemanticsProperties.ContentDescription)
        ?.joinToString(" ")
        .orEmpty()
        .ifBlank {
            config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
        }.ifBlank { config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty() }

private fun SemanticsNode.describe(): String =
    config.getOrNull(SemanticsProperties.TestTag) ?: name().ifBlank { "node $id" }

/**
 * Reading order is tree order. Two spoken nodes are in order when the later one does not start higher on the
 * screen than the earlier one by more than half the earlier one's height (items side by side share a row), or starts
 * to the right of the earlier one (the next column).
 */
private fun orderFindings(all: List<SemanticsNode>): List<String> {
    val spoken =
        all.filter {
            it.name().isNotBlank() && !it.isHidden() && it.isPlaced() &&
                it.children.none { child -> child.name().isNotBlank() }
        }
    return spoken.zipWithNext().mapNotNull { (before, after) ->
        val tolerance = before.boundsInRoot.height / 2
        val nextColumn = after.boundsInRoot.left >= before.boundsInRoot.right - 1f
        if (!nextColumn && after.boundsInRoot.top < before.boundsInRoot.top - tolerance) {
            "reading order: '${after.name().take(30)}' is read after '${before.name().take(30)}' but sits above it"
        } else {
            null
        }
    }
}
