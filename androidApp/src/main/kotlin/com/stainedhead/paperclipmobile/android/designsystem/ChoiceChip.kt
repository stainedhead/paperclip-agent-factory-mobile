package com.stainedhead.paperclipmobile.android.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.stainedhead.paperclipmobile.android.R

private val ChipGlyph = 16.dp

/** A pill for one choice among several (a company, a factory); a fill and a check show it, and it is announced. */
@Composable
fun ChoiceChip(
    text: String,
    isSelected: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .heightIn(min = Spacing.minimumTarget)
            .clip(RoundedCornerShape(Radius.chip))
            .background(if (isSelected) colors.accentSoft else colors.muted)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Spacing.extraLarge)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        if (isSelected) {
            Icon(painterResource(R.drawable.ic_check), null, Modifier.size(ChipGlyph), tint = colors.accent)
        }
        Text(text, style = AppType.body, color = if (isSelected) colors.accent else colors.textPrimary)
    }
}
