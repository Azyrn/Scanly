package com.skeler.scanely.core.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import com.skeler.scanely.BuildConfig
import java.security.MessageDigest

/**
 * Decodes the bundled free-tier provider keys that ship XOR+Base64 obfuscated in
 * [BuildConfig] (see the encoder in app/build.gradle.kts). Nothing here makes a
 * client-side key unrecoverable — a determined attacker with a rooted device and
 * a runtime hook can still read it. The goal is narrower: keep keys out of the
 * APK as plaintext strings/resources so they can't be lifted by simply unpacking
 * or running `strings`, and to void them for repackaged (re-signed) builds.
 */
object Secrets {

    // Set once from App.onCreate; a repackaged/re-signed release fails this and gets no keys.
    @Volatile
    private var signatureOk = true

    // Rebuilt from split arrays so no contiguous seed literal survives in the DEX.
    private val pepper: ByteArray by lazy {
        val a = intArrayOf(
            0x09, 0x39, 0x34, 0x36, 0x23, 0xC0, 0x4D, 0x18,
            0x99, 0x52, 0xB4, 0x01, 0x2B, 0x77, 0xAA, 0x3E
        )
        ByteArray(a.size) { ((a[it] xor 0x5A) and 0xFF).toByte() }
    }

    val gemini: String get() = decode(BuildConfig.GEMINI_API_KEY)
    val openRouter: String get() = decode(BuildConfig.OPENROUTER_API_KEY)
    val mistral: String get() = decode(BuildConfig.MISTRAL_API_KEY)
    val huggingFace: String get() = decode(BuildConfig.HUGGINGFACE_API_KEY)
    val nvidia: String get() = decode(BuildConfig.NVIDIA_API_KEY)
    val groq: String get() = decode(BuildConfig.GROQ_API_KEY)
    val cerebras: String get() = decode(BuildConfig.CEREBRAS_API_KEY)
    val cloudflareToken: String get() = decode(BuildConfig.CLOUDFLARE_API_KEY)
    val cloudflareAccountId: String get() = decode(BuildConfig.CLOUDFLARE_ACCOUNT_ID)

    fun init(context: Context) {
        signatureOk = signatureValid(context)
    }

    private fun decode(blob: String): String {
        if (blob.isEmpty() || !signatureOk) return ""
        return try {
            val data = Base64.decode(blob, Base64.NO_WRAP)
            val ks = keystream(pepper, data.size)
            String(ByteArray(data.size) { (data[it].toInt() xor ks[it].toInt()).toByte() }, Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    // Debug builds are signed with the debug key, so the pin only applies to release.
    // An empty expected value means the keystore wasn't readable at build time — pin off.
    private fun signatureValid(context: Context): Boolean {
        if (BuildConfig.DEBUG || BuildConfig.EXPECTED_SIGNATURE.isEmpty()) return true
        val actual = currentSignatureSha256(context) ?: return false
        return actual.equals(BuildConfig.EXPECTED_SIGNATURE, ignoreCase = true)
    }

    @Suppress("DEPRECATION")
    private fun currentSignatureSha256(context: Context): String? = try {
        val pm = context.packageManager
        val signature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                .signatures?.firstOrNull()
        }
        signature?.let { sha256Hex(it.toByteArray()) }
    } catch (_: Exception) {
        null
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02X".format(it) }

    // SHA-256(seed || counter) blocks concatenated; mirrors the build-time encoder.
    private fun keystream(seed: ByteArray, len: Int): ByteArray {
        val out = ByteArray(len)
        var pos = 0
        var counter = 0
        while (pos < len) {
            val md = MessageDigest.getInstance("SHA-256")
            md.update(seed)
            md.update(
                byteArrayOf(
                    (counter ushr 24).toByte(), (counter ushr 16).toByte(),
                    (counter ushr 8).toByte(), counter.toByte()
                )
            )
            val block = md.digest()
            val n = minOf(block.size, len - pos)
            System.arraycopy(block, 0, out, pos, n)
            pos += n
            counter++
        }
        return out
    }
}
