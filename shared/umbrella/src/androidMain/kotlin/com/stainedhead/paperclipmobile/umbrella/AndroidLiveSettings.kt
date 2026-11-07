package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.infrastructure.securestore.KeystoreSecretStore

/**
 * What the Android app chooses when it opens the live graph (AND-004), the counterpart of `IosLiveSettings`. The
 * sentences are the localized text the system prompt shows: [unlockReason] when unlocking the app, [confirmReason]
 * before a destructive action, [writeReason] when a board key that must be protected is stored and [keyReadReason] when
 * a call to the factory reads it (F-3, ADR-151). [keyAliasPrefix] keeps this app's Keystore keys apart from any other
 * use of the Keystore. The database and the secret records live in the app's `noBackupFilesDir`, so there is no data
 * directory to choose.
 */
data class AndroidLiveSettings(
    val unlockReason: String,
    val confirmReason: String,
    val writeReason: String,
    val keyReadReason: String,
    val keyAliasPrefix: String = KeystoreSecretStore.DEFAULT_KEY_ALIAS_PREFIX,
) {
    init {
        require(keyAliasPrefix.isNotBlank()) { "The key alias prefix must not be blank" }
    }
}
