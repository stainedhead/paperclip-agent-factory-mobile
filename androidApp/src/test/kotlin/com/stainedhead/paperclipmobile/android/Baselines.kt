package com.stainedhead.paperclipmobile.android

import androidx.compose.ui.test.SemanticsNodeInteraction
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage

/**
 * The screenshot tests run on fixed demo data, so a baseline and a fresh capture of unchanged code are the same image:
 * any changed pixel is a change, not noise. Roborazzi's default tolerates 1 percent of the pixels (R-031), which
 * hides a recoloured pill or a lost icon on a tall baseline.
 */
private val EXACT = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0f))

/** Records or verifies [path] with no pixel tolerance; screenshot tests use this, never captureRoboImage. */
fun SemanticsNodeInteraction.captureBaseline(path: String) = captureRoboImage(path, roborazziOptions = EXACT)
