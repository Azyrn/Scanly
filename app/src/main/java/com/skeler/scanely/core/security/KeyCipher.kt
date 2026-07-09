package com.skeler.scanely.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypts the user's own API keys at rest with an AES-256-GCM key that's
 * generated inside — and never leaves — the device's hardware-backed
 * Keystore. This is a different concern from [Secrets]: that object hides
 * the *bundled* shared-tier keys inside the APK; this one protects keys the
 * user types in themselves, which [Secrets]'s XOR obfuscation can't touch
 * since they never pass through the build-time encoder. DataStore itself
 * stores plain text, so this is what keeps a personal key unreadable to
 * anything short of the app's own process: root file access, a backup
 * extraction, another app on a rooted device. Because the key is bound to
 * this device's Keystore, ciphertext restored onto a different device can't
 * be decrypted either — see [decrypt] returning null.
 */
@Singleton
class KeyCipher @Inject constructor() {

    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, secretKey())
        }
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        // The GCM IV isn't secret — it travels with the ciphertext it seals.
        val payload = cipher.iv + ciphertext
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    /** Null on any failure — a corrupt blob or one sealed on another device is treated as absent. */
    fun decrypt(encoded: String): String? = try {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = payload.copyOfRange(0, IV_SIZE_BYTES)
        val ciphertext = payload.copyOfRange(IV_SIZE_BYTES, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        }
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }

    private fun secretKey(): SecretKey =
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: generateKey()

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "scanely_api_key_cipher"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE_BYTES = 12
        const val TAG_LENGTH_BITS = 128
    }
}
