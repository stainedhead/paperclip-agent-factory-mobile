package com.stainedhead.paperclipmobile.presentation

/**
 * The colour family of a status pill or line (R-019). Which family a status gets is a rule, decided once here; the apps
 * only choose the colour for each family, and the word still says what the status is, so colour never stands alone.
 */
enum class ToneUi {
    Neutral,
    Success,
    Warn,
    Critical,
}
