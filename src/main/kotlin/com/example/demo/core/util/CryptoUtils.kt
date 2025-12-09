package com.example.demo.core.util

import org.springframework.stereotype.Component
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Component
object CryptoUtils {

    /**
     * Генерация пары ключей (ECDSA P-256)
     * Используется при первом входе пользователя
     */
    fun generateKeyPair(): Pair<String, String> {
        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(ECGenParameterSpec("secp256r1")) // P-256, безопасная кривая
        val keyPair = keyGen.generateKeyPair()

        val publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.public.encoded)
        val privateKeyBase64 = Base64.getEncoder().encodeToString(keyPair.private.encoded)

        return Pair(publicKeyBase64, privateKeyBase64)
    }

    /**
     * Шифрование приватного ключа с использованием пароля пользователя
     * (Для хранения на сервере - опционально, можно хранить только на клиенте)
     *
     * В production лучше хранить приватный ключ ТОЛЬКО на устройстве (Keystore/Keychain)
     */
    fun encryptPrivateKey(privateKeyBase64: String, password: String): String {
        val salt = ByteArray(16).apply { SecureRandom().nextBytes(this) }
        val key = deriveKeyFromPassword(password, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(privateKeyBase64.toByteArray())

        // Формат: salt(16) + iv(12) + encryptedData
        val combined = salt + iv + encryptedBytes
        return Base64.getEncoder().encodeToString(combined)
    }

    /**
     * Расшифровка приватного ключа
     */
    fun decryptPrivateKey(encryptedBase64: String, password: String): String {
        val combined = Base64.getDecoder().decode(encryptedBase64)

        val salt = combined.sliceArray(0..15)
        val iv = combined.sliceArray(16..27)
        val encryptedData = combined.sliceArray(28 until combined.size)

        val key = deriveKeyFromPassword(password, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))

        val decryptedBytes = cipher.doFinal(encryptedData)
        return String(decryptedBytes)
    }

    /**
     * Генерация AES ключа из пароля (PBKDF2)
     */
    private fun deriveKeyFromPassword(password: String, salt: ByteArray): SecretKey {
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, 100000, 256)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    /**
     * Генерация случайного nonce (для защиты от replay-атак)
     */
    fun generateNonce(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    /**
     * Хеширование (SHA-256) для создания уникальных идентификаторов транзакций
     */
    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray())
        return Base64.getEncoder().encodeToString(hash)
    }
}