package com.stainedhead.paperclipmobile.infrastructure.securestore

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFArrayGetCount
import platform.CoreFoundation.CFArrayGetValueAtIndex
import platform.CoreFoundation.CFArrayRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryGetValue
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFRetain
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanFalse
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSMutableData
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.dataWithLength
import platform.LocalAuthentication.LAContext
import platform.Security.SecAccessControlCreateWithFlags
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecParam
import platform.Security.errSecSuccess
import platform.Security.kSecAccessControlBiometryCurrentSet
import platform.Security.kSecAccessControlUserPresence
import platform.Security.kSecAttrAccessControl
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecAttrSynchronizable
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitAll
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnAttributes
import platform.Security.kSecReturnData
import platform.Security.kSecUseAuthenticationContext
import platform.Security.kSecValueData
import platform.posix.memcpy
import platform.posix.memset

/**
 * The real Keychain behind [KeychainBackend] (AD-030). Every query names the service, `kSecAttrSynchronizable = false`
 * (so an item can never sync through iCloud Keychain, and an iCloud copy is never matched) and the generic password
 * class. Values travel in `NSMutableData` buffers that are zeroed after use; the framework-owned `NSData` returned by a
 * read cannot be wiped and is released as soon as its bytes are copied.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class SecurityKeychain(
    private val service: String,
    private val accessControlFor: (Protection) -> CFTypeRef? = ::createAccessControl,
) : KeychainBackend {
    override fun add(
        account: String,
        data: ByteArray,
        protection: Protection,
    ): Int =
        Query(service, account).use { query ->
            val buffer = NSMutableData.dataWithLength(data.size.convert())!!
            try {
                data.usePinned { memcpy(buffer.mutableBytes, it.addressOf(0), data.size.convert()) }
                query.setOwned(kSecValueData, CFBridgingRetain(buffer))
                // A protected item that cannot be protected is refused before the add: SecItemAdd would otherwise
                // store it with the default, backup-eligible accessibility (review1b F-C-1).
                if (query.protect(protection, accessControlFor)) SecItemAdd(query.dictionary, null) else errSecParam
            } finally {
                memset(buffer.mutableBytes, 0, buffer.length)
            }
        }

    override fun read(
        account: String,
        reason: String,
    ): ReadOutcome =
        Query(service, account).use { query ->
            query[kSecReturnData] = kCFBooleanTrue
            query[kSecMatchLimit] = kSecMatchLimitOne
            query.setOwned(
                kSecUseAuthenticationContext,
                CFBridgingRetain(LAContext().apply { localizedReason = reason }),
            )
            memScoped {
                val result = alloc<CFTypeRefVar>()
                val status = SecItemCopyMatching(query.dictionary, result.ptr)
                val data = if (status == errSecSuccess) CFBridgingRelease(result.value) as? NSData else null
                ReadOutcome(status, data?.let { it.bytes?.readBytes(it.length.toInt()) ?: ByteArray(0) })
            }
        }

    override fun exists(account: String): Int =
        Query(service, account).use { query ->
            query.attributesOnly()
            query[kSecMatchLimit] = kSecMatchLimitOne
            memScoped {
                val result = alloc<CFTypeRefVar>()
                val status = SecItemCopyMatching(query.dictionary, result.ptr)
                if (status == errSecSuccess) CFBridgingRelease(result.value)
                status
            }
        }

    override fun delete(account: String): Int = Query(service, account).use { SecItemDelete(it.dictionary) }

    override fun accounts(prefix: String): AccountsOutcome =
        Query(service, account = null).use { query ->
            query.attributesOnly()
            query[kSecMatchLimit] = kSecMatchLimitAll
            memScoped {
                val result = alloc<CFTypeRefVar>()
                val status = SecItemCopyMatching(query.dictionary, result.ptr)
                val accounts = if (status == errSecSuccess) accountsOf(result.value) else emptyList()
                AccountsOutcome(status, accounts.filter { it.startsWith(prefix) })
            }
        }

    private fun accountsOf(array: CFTypeRef?): List<String> {
        val rows: CFArrayRef = array!!.reinterpret()
        val accounts =
            (0 until CFArrayGetCount(rows)).mapNotNull { index ->
                val row: CFDictionaryRef = CFArrayGetValueAtIndex(rows, index)!!.reinterpret()
                CFDictionaryGetValue(row, kSecAttrAccount)?.let { CFBridgingRelease(CFRetain(it)) as? String }
            }
        CFRelease(array)
        return accounts
    }
}

/** One Keychain query dictionary; closing it releases everything it created. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class Query(
    service: String,
    account: String?,
) : AutoCloseable {
    val dictionary: CFMutableDictionaryRef =
        CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)!!

    init {
        this[kSecClass] = kSecClassGenericPassword
        this[kSecAttrSynchronizable] = kCFBooleanFalse
        setOwned(kSecAttrService, CFBridgingRetain(NSString.create(string = service)))
        account?.let { setOwned(kSecAttrAccount, CFBridgingRetain(NSString.create(string = it))) }
    }

    operator fun set(
        key: CPointer<*>?,
        value: CFTypeRef?,
    ) = CFDictionaryAddValue(dictionary, key, value)

    /** Adds a +1 retained object; the dictionary keeps its own reference, so ours is released at once. */
    fun setOwned(
        key: CPointer<*>?,
        value: CFTypeRef?,
    ) {
        CFDictionaryAddValue(dictionary, key, value)
        CFRelease(value)
    }

    /** Attributes only (no value is read) and no authentication prompt, even for a protected item. */
    fun attributesOnly() {
        this[kSecReturnAttributes] = kCFBooleanTrue
        setOwned(kSecUseAuthenticationContext, CFBridgingRetain(LAContext().apply { interactionNotAllowed = true }))
    }

    /**
     * Accessibility is part of the access control for protected items, so the two attributes are never both set.
     * Returns false, setting neither, when a protected item's access control cannot be created.
     */
    fun protect(
        protection: Protection,
        accessControlFor: (Protection) -> CFTypeRef?,
    ): Boolean {
        if (protection == Protection.None) {
            this[kSecAttrAccessible] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
            return true
        }
        val control = accessControlFor(protection) ?: return false
        setOwned(kSecAttrAccessControl, control)
        return true
    }

    override fun close() = CFRelease(dictionary)
}

/** The access control of a protected item: this device only, while unlocked, behind the biometric set or presence. */
@OptIn(ExperimentalForeignApi::class)
private fun createAccessControl(protection: Protection): CFTypeRef? {
    val flags =
        when (protection) {
            Protection.None -> return null
            Protection.BiometryCurrentSet -> kSecAccessControlBiometryCurrentSet
            Protection.UserPresence -> kSecAccessControlUserPresence
        }
    return SecAccessControlCreateWithFlags(null, kSecAttrAccessibleWhenUnlockedThisDeviceOnly, flags, null)
}
