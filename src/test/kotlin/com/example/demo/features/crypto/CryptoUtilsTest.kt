package com.example.demo.features.crypto

import com.example.demo.core.util.CryptoUtils
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.security.KeyFactory
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

@DisplayName("CryptoUtils - Утилиты криптографии")
class CryptoUtilsTest {

    @Test
    @DisplayName("Генерация пары ключей создает валидные ключи")
    fun `generateKeyPair should create valid key pair`() {
        // When
        val (publicKey, privateKey) = CryptoUtils.generateKeyPair()

        // Then
        assertNotNull(publicKey)
        assertNotNull(privateKey)
        assertTrue(publicKey.isNotEmpty())
        assertTrue(privateKey.isNotEmpty())

        // Проверяем, что это валидный Base64
        assertDoesNotThrow { Base64.getDecoder().decode(publicKey) }
        assertDoesNotThrow { Base64.getDecoder().decode(privateKey) }
    }

    @Test
    @DisplayName("Каждая генерация создает уникальные ключи")
    fun `each key generation should be unique`() {
        // When
        val pair1 = CryptoUtils.generateKeyPair()
        val pair2 = CryptoUtils.generateKeyPair()

        // Then
        assertNotEquals(pair1.first, pair2.first, "Публичные ключи должны отличаться")
        assertNotEquals(pair1.second, pair2.second, "Приватные ключи должны отличаться")
    }

    @Test
    @DisplayName("Формат ключей совместим с EC PKCS#8/X.509")
    fun `generated keys should be EC PKCS8 and X509`() {
        // Given
        val (publicKeyBase64, privateKeyBase64) = CryptoUtils.generateKeyPair()
        val keyFactory = KeyFactory.getInstance("EC")

        // When
        val privateKey = keyFactory.generatePrivate(
            PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyBase64))
        )
        val publicKey = keyFactory.generatePublic(
            X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64))
        )

        // Then
        assertTrue(privateKey is ECPrivateKey, "Приватный ключ должен быть EC")
        assertTrue(publicKey is ECPublicKey, "Публичный ключ должен быть EC")
        assertEquals("PKCS#8", privateKey.format)
        assertEquals("X.509", publicKey.format)
    }

    @Test
    @DisplayName("Шифрование и расшифровка приватного ключа")
    fun `encrypt and decrypt private key should work`() {
        // Given
        val originalKey = "test-private-key-data-12345"
        val password = "strong-password-123"

        // When
        val encrypted = CryptoUtils.encryptPrivateKey(originalKey, password)
        val decrypted = CryptoUtils.decryptPrivateKey(encrypted, password)

        // Then
        assertEquals(originalKey, decrypted, "Расшифрованный ключ должен совпадать с оригиналом")
    }

    @Test
    @DisplayName("Расшифровка с неверным паролем должна выбросить исключение")
    fun `decrypt with wrong password should fail`() {
        // Given
        val originalKey = "test-private-key"
        val correctPassword = "password123"
        val wrongPassword = "wrong-password"

        val encrypted = CryptoUtils.encryptPrivateKey(originalKey, correctPassword)

        // When & Then
        assertThrows(Exception::class.java) {
            CryptoUtils.decryptPrivateKey(encrypted, wrongPassword)
        }
    }

    @Test
    @DisplayName("Генерация nonce создает уникальные значения")
    fun `generateNonce should create unique values`() {
        // When
        val nonce1 = CryptoUtils.generateNonce()
        val nonce2 = CryptoUtils.generateNonce()

        // Then
        assertNotNull(nonce1)
        assertNotNull(nonce2)
        assertNotEquals(nonce1, nonce2, "Каждый nonce должен быть уникальным")

        // Проверяем Base64
        assertDoesNotThrow { Base64.getDecoder().decode(nonce1) }
    }

    @Test
    @DisplayName("SHA-256 хеширование должно быть детерминированным")
    fun `sha256 should be deterministic`() {
        // Given
        val input = "test-string-for-hashing"

        // When
        val hash1 = CryptoUtils.sha256(input)
        val hash2 = CryptoUtils.sha256(input)

        // Then
        assertEquals(hash1, hash2, "Одинаковый вход должен давать одинаковый хеш")
    }

    @Test
    @DisplayName("Разные строки должны давать разные хеши")
    fun `different inputs should produce different hashes`() {
        // Given
        val input1 = "string1"
        val input2 = "string2"

        // When
        val hash1 = CryptoUtils.sha256(input1)
        val hash2 = CryptoUtils.sha256(input2)

        // Then
        assertNotEquals(hash1, hash2, "Разные строки должны давать разные хеши")
    }

    @Test
    @DisplayName("SHA-256 должен создавать валидный Base64")
    fun `sha256 should create valid base64`() {
        // Given
        val input = "test-input"

        // When
        val hash = CryptoUtils.sha256(input)

        // Then
        assertDoesNotThrow { Base64.getDecoder().decode(hash) }
    }
}
