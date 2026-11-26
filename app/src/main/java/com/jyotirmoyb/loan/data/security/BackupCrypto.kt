package com.jyotirmoyb.loan.data.security

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@JsonClass(generateAdapter = true)
data class EncryptedBackupEnvelope(
    val format: String = FORMAT_V1,
    val kdf: String = KDF_ALGO,
    val iterations: Int = PBKDF2_ITERATIONS,
    val cipher: String = CIPHER_ALGO,
    val saltB64: String,
    val ivB64: String,
    val ciphertextB64: String
) {
    companion object {
        const val FORMAT_V1 = "LOANJB_ENC_V1"
        const val KDF_ALGO = "PBKDF2WithHmacSHA256"
        const val CIPHER_ALGO = "AES/GCM/NoPadding"
        const val PBKDF2_ITERATIONS = 120_000
    }
}

/**
 * Pure JVM password-based AES-256-GCM encryption for offline LoanJB JSON backups.
 *
 * Security invariants:
 * - Random 16-byte salt per export via [SecureRandom].
 * - Key derivation via PBKDF2WithHmacSHA256 (120,000 iterations, 256-bit AES key).
 * - Random 12-byte GCM nonce per export, 128-bit authentication tag, AAD bound to format version.
 * - Any password mismatch or ciphertext tampering fails authentication deterministically.
 */
object BackupCrypto {

    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    const val MIN_PASSWORD_LENGTH = 6

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val envelopeAdapter = moshi.adapter(EncryptedBackupEnvelope::class.java).indent("  ")

    fun isEncryptedEnvelope(rawContent: String): Boolean {
        val trimmed = rawContent.trim()
        if (!trimmed.startsWith("{")) return false
        return trimmed.contains("\"${EncryptedBackupEnvelope.FORMAT_V1}\"") &&
            trimmed.contains("\"ciphertextB64\"")
    }

    fun encryptJson(plainJson: String, password: String): Result<String> = runCatching {
        require(password.length >= MIN_PASSWORD_LENGTH) {
            "Backup password must be at least $MIN_PASSWORD_LENGTH characters."
        }
        val rng = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also { rng.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { rng.nextBytes(it) }
        val keyBytes = deriveKey(password.toCharArray(), salt, EncryptedBackupEnvelope.PBKDF2_ITERATIONS)
        try {
            val secretKey = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance(EncryptedBackupEnvelope.CIPHER_ALGO)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(EncryptedBackupEnvelope.FORMAT_V1.toByteArray(StandardCharsets.UTF_8))
            val ciphertext = cipher.doFinal(plainJson.toByteArray(StandardCharsets.UTF_8))
            val envelope = EncryptedBackupEnvelope(
                format = EncryptedBackupEnvelope.FORMAT_V1,
                kdf = EncryptedBackupEnvelope.KDF_ALGO,
                iterations = EncryptedBackupEnvelope.PBKDF2_ITERATIONS,
                cipher = EncryptedBackupEnvelope.CIPHER_ALGO,
                saltB64 = Base64Compat.encode(salt),
                ivB64 = Base64Compat.encode(iv),
                ciphertextB64 = Base64Compat.encode(ciphertext)
            )
            envelopeAdapter.toJson(envelope)
        } finally {
            keyBytes.fill(0)
        }
    }

    fun decryptJson(rawContent: String, password: String): Result<String> {
        val envelope = runCatching { envelopeAdapter.fromJson(rawContent) }.getOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid encrypted backup format."))
        if (envelope.format != EncryptedBackupEnvelope.FORMAT_V1) {
            return Result.failure(IllegalArgumentException("Unsupported encrypted backup version: ${envelope.format}"))
        }
        if (password.isEmpty()) {
            return Result.failure(IllegalArgumentException("Password is required to decrypt this backup."))
        }
        if (envelope.iterations !in 10_000..1_000_000) {
            return Result.failure(IllegalArgumentException("Unsupported key derivation iteration count."))
        }
        val salt = runCatching { Base64Compat.decode(envelope.saltB64) }.getOrNull()
            ?: return Result.failure(IllegalArgumentException("Corrupted backup salt."))
        val iv = runCatching { Base64Compat.decode(envelope.ivB64) }.getOrNull()
            ?: return Result.failure(IllegalArgumentException("Corrupted backup IV."))
        val ciphertext = runCatching { Base64Compat.decode(envelope.ciphertextB64) }.getOrNull()
            ?: return Result.failure(IllegalArgumentException("Corrupted backup ciphertext."))

        val keyBytes = deriveKey(password.toCharArray(), salt, envelope.iterations)
        return try {
            val secretKey = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance(EncryptedBackupEnvelope.CIPHER_ALGO)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(envelope.format.toByteArray(StandardCharsets.UTF_8))
            val plainBytes = cipher.doFinal(ciphertext)
            Result.success(String(plainBytes, StandardCharsets.UTF_8))
        } catch (_: AEADBadTagException) {
            Result.failure(IllegalArgumentException("Incorrect password or backup file was modified."))
        } catch (_: GeneralSecurityException) {
            Result.failure(IllegalArgumentException("Could not decrypt backup. Check your password and file integrity."))
        } finally {
            keyBytes.fill(0)
        }
    }

    internal fun deriveKey(passwordChars: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(passwordChars, salt, iterations, KEY_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance(EncryptedBackupEnvelope.KDF_ALGO)
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}

/**
 * Pure-Kotlin RFC 4648 Base64 codec compatible with both Android API 24+ and standard JVM unit tests.
 */
internal object Base64Compat {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private val DECODE_TABLE = IntArray(128) { -1 }.apply {
        ALPHABET.forEachIndexed { idx, ch -> this[ch.code] = idx }
    }

    fun encode(data: ByteArray): String {
        val out = StringBuilder(((data.size + 2) / 3) * 4)
        var i = 0
        while (i < data.size) {
            val b0 = data[i].toInt() and 0xFF
            val hasB1 = i + 1 < data.size
            val hasB2 = i + 2 < data.size
            val b1 = if (hasB1) data[i + 1].toInt() and 0xFF else 0
            val b2 = if (hasB2) data[i + 2].toInt() and 0xFF else 0

            val triple = (b0 shl 16) or (b1 shl 8) or b2
            out.append(ALPHABET[(triple ushr 18) and 0x3F])
            out.append(ALPHABET[(triple ushr 12) and 0x3F])
            out.append(if (hasB1) ALPHABET[(triple ushr 6) and 0x3F] else '=')
            out.append(if (hasB2) ALPHABET[triple and 0x3F] else '=')
            i += 3
        }
        return out.toString()
    }

    fun decode(encoded: String): ByteArray {
        val cleaned = encoded.filter { !it.isWhitespace() }
        require(cleaned.length % 4 == 0) { "Invalid Base64 length" }
        if (cleaned.isEmpty()) return ByteArray(0)

        val padding = when {
            cleaned.endsWith("==") -> 2
            cleaned.endsWith("=") -> 1
            else -> 0
        }
        val outLen = (cleaned.length / 4) * 3 - padding
        val out = ByteArray(outLen)
        var outIdx = 0
        var i = 0
        while (i < cleaned.length) {
            val c0 = decodeChar(cleaned[i])
            val c1 = decodeChar(cleaned[i + 1])
            val c2 = if (cleaned[i + 2] == '=') 0 else decodeChar(cleaned[i + 2])
            val c3 = if (cleaned[i + 3] == '=') 0 else decodeChar(cleaned[i + 3])

            val triple = (c0 shl 18) or (c1 shl 12) or (c2 shl 6) or c3
            if (outIdx < outLen) out[outIdx++] = ((triple ushr 16) and 0xFF).toByte()
            if (outIdx < outLen) out[outIdx++] = ((triple ushr 8) and 0xFF).toByte()
            if (outIdx < outLen) out[outIdx++] = (triple and 0xFF).toByte()
            i += 4
        }
        return out
    }

    private fun decodeChar(ch: Char): Int {
        val code = ch.code
        require(code in 0..127 && DECODE_TABLE[code] >= 0) { "Invalid Base64 character" }
        return DECODE_TABLE[code]
    }
}
