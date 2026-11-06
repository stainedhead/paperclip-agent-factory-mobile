package com.stainedhead.paperclipmobile.infrastructure.persistence

/**
 * The database could not be opened for a reason that does not prove it is damaged: the file is busy or locked, the disk
 * is full or failing, protected data is not available yet (a launch before the first unlock), or a migration threw.
 * Nothing was deleted. The caller shows "storage unavailable" and may try again later; deleting the database here would
 * destroy the factory list for a condition that can pass (review1b F-B-1, ADR-077).
 */
class StorageUnavailableException(
    cause: Throwable,
) : Exception("The factory database is not available right now", cause)
