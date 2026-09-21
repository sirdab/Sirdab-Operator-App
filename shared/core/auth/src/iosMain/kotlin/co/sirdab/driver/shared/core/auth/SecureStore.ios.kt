package co.sirdab.driver.shared.core.auth

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * Keychain, generic-password class, scoped to this app's service name.
 *
 * `AfterFirstUnlockThisDeviceOnly` is the right accessibility for a driver app: the session must
 * survive a reboot so a background sync can run before the driver unlocks the phone, but it must
 * never ride an iCloud or iTunes backup onto another device.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class KeychainSecureStore : SecureStore {

    override suspend fun put(key: String, value: String) {
        remove(key)

        val data = NSString.create(string = value).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        val retainedData = CFBridgingRetain(data)
        val query = baseQuery(key)
        try {
            CFDictionaryAddValue(query, kSecValueData, retainedData)
            CFDictionaryAddValue(query, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
            SecItemAdd(query, null)
        } finally {
            CFRelease(query)
            CFRelease(retainedData)
        }
    }

    override suspend fun get(key: String): String? {
        val query = baseQuery(key)
        try {
            CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)

            return memScoped {
                val found = alloc<CFTypeRefVar>()
                if (SecItemCopyMatching(query, found.ptr) != errSecSuccess) return@memScoped null

                // CFBridgingRelease takes ownership of the copy SecItemCopyMatching handed back.
                val data = CFBridgingRelease(found.value) as? NSData ?: return@memScoped null
                NSString.create(data = data, encoding = NSUTF8StringEncoding) as String?
            }
        } finally {
            CFRelease(query)
        }
    }

    override suspend fun remove(key: String) {
        val query = baseQuery(key)
        try {
            SecItemDelete(query)
        } finally {
            CFRelease(query)
        }
    }

    /** Class, service and account: the three attributes that identify one stored secret. */
    private fun baseQuery(key: String): CFMutableDictionaryRef {
        val query = CFDictionaryCreateMutable(
            null,
            0,
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr,
        ) ?: error("could not allocate a keychain query")

        val service = CFBridgingRetain(NSString.create(string = SERVICE))
        val account = CFBridgingRetain(NSString.create(string = key))
        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(query, kSecAttrService, service)
        CFDictionaryAddValue(query, kSecAttrAccount, account)
        // The dictionary retained both, so the local references are done.
        CFRelease(service)
        CFRelease(account)
        return query
    }

    private companion object {
        const val SERVICE = "co.sirdab.driver.session"
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun releaseIfPresent(pointer: CPointer<*>?) {
    if (pointer != null) platform.CoreFoundation.CFRelease(pointer)
}
