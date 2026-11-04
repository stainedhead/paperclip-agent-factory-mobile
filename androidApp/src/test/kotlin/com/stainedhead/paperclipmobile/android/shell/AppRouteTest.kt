package com.stainedhead.paperclipmobile.android.shell

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.junit.jupiter.api.Test

/** AND-012: the route table is the same as the iPhone app's, and a route is data (ids only), ready for deep links. */
class AppRouteTest {
    @Test
    fun `the five tabs are in display order`() {
        AppTab.entries shouldBe listOf(AppTab.Inbox, AppTab.Factory, AppTab.Work, AppTab.Agents, AppTab.Factories)
    }

    @Test
    fun `only the inbox carries a badge and only when something waits`() {
        AppTab.Inbox.badge(inboxBadgeCount = 3) shouldBe 3
        AppTab.Inbox.badge(inboxBadgeCount = 0) shouldBe null
        for (tab in AppTab.entries - AppTab.Inbox) tab.badge(inboxBadgeCount = 3) shouldBe null
    }

    @Test
    fun `the route table lists every pushed screen`() {
        AppRoute::class.sealedSubclasses.map { it.simpleName }.toSet() shouldBe
            setOf(
                "Decision",
                "Approval",
                "Issue",
                "Agent",
                "Run",
                "ManageFactories",
                "EditFactory",
                "Settings",
                "PairedDevices",
            )
    }

    @Test
    fun `a route carries ids only and survives a round trip`() {
        val routes: List<AppRoute> =
            listOf(
                AppRoute.Decision("dec-1"),
                AppRoute.Approval("ap-1"),
                AppRoute.Issue("PAP-1"),
                AppRoute.Agent("ag-1"),
                AppRoute.Run("run-1"),
                AppRoute.ManageFactories,
                AppRoute.EditFactory(null),
                AppRoute.EditFactory("inst-1"),
                AppRoute.Settings,
                AppRoute.PairedDevices,
            )
        val serializer = serializer<AppRoute>()

        for (route in routes) {
            Json.decodeFromString(serializer, Json.encodeToString(serializer, route)) shouldBe route
        }
    }

    @Test
    fun `the budget incident is a sheet and settings can be a sheet too`() {
        AppSheet::class.sealedSubclasses.map { it.simpleName }.toSet() shouldBe setOf("BudgetIncident", "Settings")
    }
}
