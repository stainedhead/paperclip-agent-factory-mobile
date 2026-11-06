package com.stainedhead.paperclipmobile.architecture

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File

/** Predictive back is declared by the manifest of every variant, not left to the platform default (R-039). */
class AndroidManifestBackRulesTest {
    private fun merged(variant: String): String {
        val path = requireNotNull(System.getProperty("pafm.mergedManifest.$variant")) { "no merged $variant manifest" }
        return File(path).readText()
    }

    @Test
    fun `every variant opts in to the back callback, so predictive back is not left to the platform default`() {
        listOf("debug", "release", "releaseDemo", "benchmark", "nonMinified").forEach {
            Assertions.assertEquals("true", applicationAttribute(merged(it), "enableOnBackInvokedCallback"), it)
        }
    }
}
