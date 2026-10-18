package com.example.floodwatch.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts persisted authentication sessions with a non-exportable Android Keystore key. */
internal object SecureSessionCipher {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "floodwatch_session_key_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFIX = "enc:v1:"
    private const val TAG_LENGTH_BITS = 128

    fun isEncrypted(value: String): Boolean = value.startsWith(PREFIX)

    fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        return buildString {
            append(PREFIX)
            append(Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            append(':')
            append(Base64.encodeToString(encrypted, Base64.NO_WRAP))
        }
    }

    fun decrypt(payload: String): String {
        require(isEncrypted(payload)) { "Unsupported encrypted session format" }
        val parts = payload.removePrefix(PREFIX).split(':', limit = 2)
        require(parts.size == 2) { "Invalid encrypted session payload" }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(TAG_LENGTH_BITS, iv)
        )
        return String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generateKey()
        }
    }
}
