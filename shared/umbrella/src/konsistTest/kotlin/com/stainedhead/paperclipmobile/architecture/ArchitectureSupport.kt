package com.stainedhead.paperclipmobile.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.container.KoScope
import java.io.File

internal const val APP_PACKAGE = "com.stainedhead.paperclipmobile"

/** Imports every layer may use besides its own inward dependencies. */
internal val STANDARD_IMPORTS = listOf("kotlin.", "kotlinx.coroutines.", "kotlinx.datetime.")

internal val projectRoot: File =
    File(requireNotNull(System.getProperty("pafm.rootDir")) { "pafm.rootDir is not set" })

/** The `commonMain` sources of one shared module, for example `domain` or `infrastructure/api`. */
internal fun scope(module: String): KoScope = Konsist.scopeFromDirectory("shared/$module/src/commonMain")
