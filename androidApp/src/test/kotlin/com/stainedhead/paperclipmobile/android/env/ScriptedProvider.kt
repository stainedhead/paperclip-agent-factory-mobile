package com.stainedhead.paperclipmobile.android.env

import com.stainedhead.paperclipmobile.umbrella.AndroidGraphOpen
import com.stainedhead.paperclipmobile.umbrella.PaperclipCore

/** A provider a test scripts: each call to [makeGraph] pops the next outcome. */
class ScriptedProvider(
    override val mode: GraphMode,
    outcomes: List<() -> AndroidGraphOpen>,
) : AppGraphProvider {
    private val remaining = ArrayDeque(outcomes)

    var calls = 0
        private set

    override fun makeGraph(): AndroidGraphOpen {
        calls++
        return remaining.removeFirst()()
    }
}

fun demoOpen() = AndroidGraphOpen.Opened(PaperclipCore.createDemoGraph())

fun firstRunOpen() = AndroidGraphOpen.Opened(PaperclipCore.createFirstRunDemoGraph())

fun unavailable() = AndroidGraphOpen.Unavailable(IllegalStateException("database locked"))
