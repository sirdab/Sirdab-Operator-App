package co.sirdab.driver.shared.core.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-GCM under a non-exportable Android Keystore key, with the ciphertext parked in ordinary
 * preferences. The key never leaves the TEE, so a copied preferences file is inert.
 */
class AndroidSecureStore(context: Context) : SecureStore {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override suspend fun put(key: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val ciphertext = cipher.doFinal(value.encodeToByteArray())
        // The IV is generated per encryption and is not secret, so it rides with the ciphertext.
        val packed = cipher.iv + ciphertext
        prefs.edit().putString(key, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    override suspend fun get(key: String): String? {
        val packed = prefs.getString(key, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null
        if (packed.size <= IV_BYTES) return null

        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, packed, 0, IV_BYTES))
            }
            cipher.doFinal(packed, IV_BYTES, packed.size - IV_BYTES).decodeToString()
        }.getOrNull()
    }

    override suspend fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val FILE = "driver.session"
        const val PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "driver.session.key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
