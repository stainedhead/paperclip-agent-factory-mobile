package com.stainedhead.paperclipmobile.android.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.stainedhead.paperclipmobile.android.R
import com.stainedhead.paperclipmobile.android.designsystem.AppButton
import com.stainedhead.paperclipmobile.android.designsystem.AppTheme
import com.stainedhead.paperclipmobile.android.designsystem.AppType
import com.stainedhead.paperclipmobile.android.designsystem.ButtonKind
import com.stainedhead.paperclipmobile.android.designsystem.Spacing
import com.stainedhead.paperclipmobile.presentation.InboxItemUi

/** The web door of a card: [open] goes through [WebOpener], [failed] says the phone could not show the page. */
internal class WebDoor(
    val usable: Boolean,
    val open: () -> Unit,
    val failed: Boolean,
)

/**
 * Opens the item in the web app through [WebOpener] (https only, Custom Tab or browser). Without a usable address there
 * is nothing to open, so there is no button; a phone that cannot show the page says so on the card.
 */
@Composable
internal fun rememberWebDoor(item: InboxItemUi): WebDoor {
    val opener = currentWebOpener()
    var failed by remember { mutableStateOf(false) }
    val url = (inboxDestination(item) as? InboxDestination.Web)?.url
    return WebDoor(usable = url != null, open = { failed = !opener.open(url) }, failed = failed)
}

@Composable
internal fun OpenInWebButton(
    item: InboxItemUi,
    web: WebDoor,
) {
    if (!web.usable) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        AppButton(
            stringResource(R.string.inbox_open_in_web),
            web.open,
            Modifier.testTag("inbox-web-${item.id}"),
            kind = ButtonKind.Secondary,
        )
        if (web.failed) {
            Text(
                stringResource(R.string.issue_open_web_failed),
                Modifier.testTag("inbox-web-failed-${item.id}"),
                style = AppType.secondary,
                color = AppTheme.colors.destructive,
            )
        }
    }
}
