package com.stainedhead.paperclipmobile.infrastructure.api

private val STUB_UUID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

/**
 * What the stub treats as an id the server can have: a UUID, as its columns are. Written here, not borrowed from the
 * adapter, so the stub can be shared with other modules' tests and does not mark the adapter's own rule right.
 */
internal fun isStubUuid(id: String): Boolean = STUB_UUID.matches(id)
