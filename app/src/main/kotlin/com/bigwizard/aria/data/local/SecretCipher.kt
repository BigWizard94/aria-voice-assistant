package com.bigwizard.aria.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts small secrets (the user's AI API key) with an AES-256-GCM key that
 * lives in the Android Keystore and never leaves it. Ciphertext is stored as
 * Base64("v1:" + IV + ciphertext).
 *
 * A restored backup or a device transfer therefore cannot reveal the key: the
 * Keystore key does not travel with it, so [decrypt] returns null and the user
 * simply re-enters the key.
 */
object SecretCipher {

    private const val TAG = "SecretCipher"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "aria_api_key_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LEN = 12
    private const val TAG_BITS = 128
    private const val PREFIX = "v1:"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    /** Returns the encoded ciphertext, or null if the Keystore is unusable. */
    fun encrypt(plain: String): String? = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        PREFIX + Base64.encodeToString(cipher.iv + ct, Base64.NO_WRAP)
    } catch (e: Exception) {
        Log.e(TAG, "Encryption failed", e)
        null
    }

    /** Returns the plaintext, or null if it cannot be decrypted (e.g. restored on a new device). */
    fun decrypt(encoded: String): String? = try {
        require(encoded.startsWith(PREFIX)) { "Unknown secret format" }
        val bytes = Base64.decode(encoded.removePrefix(PREFIX), Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(TAG_BITS, bytes, 0, IV_LEN)
        )
        String(cipher.doFinal(bytes, IV_LEN, bytes.size - IV_LEN), Charsets.UTF_8)
    } catch (e: Exception) {
        Log.w(TAG, "Could not decrypt stored secret", e)
        null
    }
}
