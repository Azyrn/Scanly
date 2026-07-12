package com.skeler.scanely.core.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

// On-device: KeyCipher needs the real Android Keystore.
@RunWith(AndroidJUnit4::class)
class KeyCipherTest {

    private val cipher = KeyCipher()

    @Test
    fun decryptReturnsExactlyWhatWasEncrypted() {
        val secret = "sk-or-v1-abcdefghijklmnopqrstuvwxyz0123456789"
        val encoded = cipher.encrypt(secret)
        assertEquals(secret, cipher.decrypt(encoded))
    }

    @Test
    fun storedFormIsNotThePlaintextKey() {
        val secret = "sk-ant-super-secret-value"
        val encoded = cipher.encrypt(secret)
        assertNotEquals(secret, encoded)
        assertFalse("ciphertext must not leak the plaintext key", encoded.contains(secret))
    }

    @Test
    fun twoEncryptionsOfTheSameKeyDoNotMatch() {
        // Random IV per encrypt — equal plaintexts must not fingerprint.
        val secret = "nvapi-same-value-both-times"
        assertNotEquals(cipher.encrypt(secret), cipher.encrypt(secret))
    }

    @Test
    fun corruptCiphertextFailsClosedInsteadOfCrashing() {
        assertNull(cipher.decrypt("not-a-real-ciphertext-blob"))
    }

    @Test
    fun tamperedCiphertextIsRejected() {
        val encoded = cipher.encrypt("gsk_groq-key-value")
        val tampered = encoded.dropLast(4) + "abcd"
        assertNull("GCM's auth tag should reject any modified ciphertext", cipher.decrypt(tampered))
    }
}
