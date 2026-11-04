package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.CardRow
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.android.labels.stringRes
import com.stainedhead.paperclipmobile.presentation.CompanyUi

private val ChoiceGlyph = 24.dp

/** One company in a single-choice list; the role is shown in words. */
@Composable
fun CompanyChoiceRow(
    company: CompanyUi,
    isChosen: Boolean,
    onChoose: () -> Unit,
) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.minimumTarget)
            .selectable(selected = isChosen, role = Role.RadioButton, onClick = onChoose)
            .testTag("company-choice-${company.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CardRow(
            title = company.name,
            help = stringResource(company.role.stringRes()),
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                painterResource(if (isChosen) R.drawable.ic_check_circle else R.drawable.ic_radio_unchecked),
                contentDescription = null,
                modifier = Modifier.size(ChoiceGlyph),
                tint = if (isChosen) colors.accent else colors.textSecondary,
            )
        }
    }
}
