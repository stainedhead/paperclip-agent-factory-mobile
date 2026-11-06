package com.stainedhead.paperclipmobile.umbrella

/**
 * What happened at launch that the person has to hear about. [databaseRecreated] is true when the factory list could
 * not be read and was replaced by an empty one, so every factory must be added again; the app shows an explanation once
 * (the SwiftUI banner is the app's job). [orphanSecretsRemoved] is how many leftover keys of those lost factories were
 * deleted from the Keychain during the same launch. Both are false/zero on a normal launch (review1b F-B-2, ADR-078).
 */
data class StartupNotice(
    val databaseRecreated: Boolean = false,
    val orphanSecretsRemoved: Int = 0,
)
