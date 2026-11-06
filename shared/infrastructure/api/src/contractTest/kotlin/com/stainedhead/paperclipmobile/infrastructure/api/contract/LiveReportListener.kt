package com.stainedhead.paperclipmobile.infrastructure.api.contract

import org.junit.platform.launcher.LauncherSession
import org.junit.platform.launcher.LauncherSessionListener

/** Prints the live-versus-MockEngine operation table once, when the contract run ends. */
class LiveReportListener : LauncherSessionListener {
    override fun launcherSessionClosed(session: LauncherSession) {
        LiveReport.write(System.getProperty("pafm.reportDir"))
    }
}
