package com.stainedhead.paperclipmobile.umbrella

/**
 * What the Android app chooses when it opens the live graph (AND-004), the counterpart of `IosLiveSettings`. The
 * sentences are the localized text the system prompt shows: [unlockReason] when unlocking the app, [confirmReason]
 * before a destructive action and [writeReason] when a board key that must be protected is stored. [keyAliasPrefix]
 * keeps this app's Keystore keys apart from any other use of the Keystore. The database and the secret records live in
 * the app's `noBackupFilesDir`, so there is no data directory to choose.
 */
data class AndroidLiveSettings(
    val unlockReason: String,
    val confirmReason: String,
    val writeReason: String,
    val keyAliasPrefix: String = DEFAULT_KEY_ALIAS_PREFIX,
) {
    init {
        require(keyAliasPrefix.isNotBlank()) { "The key alias prefix must not be blank" }
    }

    companion object {
        const val DEFAULT_KEY_ALIAS_PREFIX = "pafm.secret/"
    }
}
