package com.example.demo.features.qr

import com.example.demo.core.database.MealType
import com.example.demo.core.util.CryptoUtils
import com.example.demo.features.qr.service.QRCodeService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import java.util.Base64
import java.util.UUID

@DisplayName("QRCodeService - Криптография QR-кодов")
class QRCodeServiceTest {

    private lateinit var qrCodeService: QRCodeService
    private lateinit var publicKey: String
    private lateinit var privateKey: String

    @BeforeEach
    fun setup() {
        qrCodeService = QRCodeService()

        // Генерируем пару ключей для тестов
        val keys = CryptoUtils.generateKeyPair()
        publicKey = keys.first
        privateKey = keys.second
    }

    @Test
    @DisplayName("Генерация подписи должна создавать валидную Base64 строку")
    fun `generateSignature should create valid base64 string`() {
        // Given
        val userId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis() / 1000
        val mealType = MealType.LUNCH
        val nonce = CryptoUtils.generateNonce()

        // When
        val signature = qrCodeService.generateSignature(userId, timestamp, mealType, nonce, privateKey)

        // Then
        assertNotNull(signature)
        assertTrue(signature.isNotEmpty())
        // Проверяем, что это валидный Base64
        assertDoesNotThrow { Base64.getDecoder().decode(signature) }
    }

    @Test
    @DisplayName("Валидная подпись должна проходить проверку")
    fun `valid signature should pass verification`() {
        // Given
        val userId = UUID.randomUUID().toString()
        val timestamp = qrCodeService.roundTimestamp(System.currentTimeMillis() / 1000)
        val mealType = MealType.BREAKFAST
        val nonce = CryptoUtils.generateNonce()

        val signature = qrCodeService.generateSignature(userId, timestamp, mealType, nonce, privateKey)

        // When
        val isValid = qrCodeService.verifySignature(userId, timestamp, mealType, nonce, signature, publicKey)

        // Then
        assertTrue(isValid, "Валидная подпись должна проходить проверку")
    }

    @Test
    @DisplayName("Подпись с измененными данными не должна проходить проверку")
    fun `signature with tampered data should fail verification`() {
        // Given
        val userId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis() / 1000
        val mealType = MealType.LUNCH
        val nonce = CryptoUtils.generateNonce()

        val signature = qrCodeService.generateSignature(userId, timestamp, mealType, nonce, privateKey)

        // When - пытаемся проверить с другим mealType
        val isValid = qrCodeService.verifySignature(
            userId, timestamp, MealType.BREAKFAST, nonce, signature, publicKey
        )

        // Then
        assertFalse(isValid, "Подпись с измененными данными не должна быть валидной")
    }

    @Test
    @DisplayName("Подпись с чужим публичным ключом не должна проходить проверку")
    fun `signature with wrong public key should fail`() {
        // Given
        val userId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis() / 1000
        val mealType = MealType.LUNCH
        val nonce = CryptoUtils.generateNonce()

        val signature = qrCodeService.generateSignature(userId, timestamp, mealType, nonce, privateKey)

        // Генерируем другую пару ключей
        val otherKeys = CryptoUtils.generateKeyPair()

        // When
        val isValid = qrCodeService.verifySignature(
            userId, timestamp, mealType, nonce, signature, otherKeys.first
        )

        // Then
        assertFalse(isValid, "Подпись должна быть невалидной с чужим публичным ключом")
    }

    @Test
    @DisplayName("Округление timestamp до 30 секунд")
    fun `roundTimestamp should round to 30 second intervals`() {
        // Given
        val timestamp1 = 1701954015L // 15 секунд
        val timestamp2 = 1701954044L // 44 секунды

        // When
        val rounded1 = qrCodeService.roundTimestamp(timestamp1)
        val rounded2 = qrCodeService.roundTimestamp(timestamp2)

        // Then
        assertEquals(1701954000L, rounded1, "Должно округлиться до 0 секунд")
        assertEquals(1701954030L, rounded2, "Должно округлиться до 30 секунд")
    }

    @Test
    @DisplayName("Проверка валидности timestamp - в допустимом окне")
    fun `isTimestampValid should return true for timestamp within window`() {
        // Given
        val currentTime = System.currentTimeMillis() / 1000
        val validTimestamp = currentTime - 30 // 30 секунд назад

        // When
        val isValid = qrCodeService.isTimestampValid(validTimestamp, currentTime)

        // Then
        assertTrue(isValid, "Timestamp в пределах 60 секунд должен быть валидным")
    }

    @Test
    @DisplayName("Проверка валидности timestamp - за пределами окна")
    fun `isTimestampValid should return false for expired timestamp`() {
        // Given
        val currentTime = System.currentTimeMillis() / 1000
        val expiredTimestamp = currentTime - 120 // 2 минуты назад

        // When
        val isValid = qrCodeService.isTimestampValid(expiredTimestamp, currentTime)

        // Then
        assertFalse(isValid, "Timestamp старше 60 секунд должен быть невалидным")
    }

    @Test
    @DisplayName("Генерация хеша транзакции должна быть детерминированной")
    fun `transaction hash should be deterministic`() {
        // Given
        val userId = UUID.randomUUID().toString()
        val timestamp = 1701954000L
        val mealType = MealType.LUNCH
        val nonce = "test-nonce-123"

        // When
        val hash1 = qrCodeService.generateTransactionHash(userId, timestamp, mealType, nonce)
        val hash2 = qrCodeService.generateTransactionHash(userId, timestamp, mealType, nonce)

        // Then
        assertEquals(hash1, hash2, "Одинаковые данные должны давать одинаковый хеш")
    }

    @Test
    @DisplayName("Разные nonce должны давать разные хеши")
    fun `different nonces should produce different hashes`() {
        // Given
        val userId = UUID.randomUUID().toString()
        val timestamp = 1701954000L
        val mealType = MealType.LUNCH
        val nonce1 = "nonce-1"
        val nonce2 = "nonce-2"

        // When
        val hash1 = qrCodeService.generateTransactionHash(userId, timestamp, mealType, nonce1)
        val hash2 = qrCodeService.generateTransactionHash(userId, timestamp, mealType, nonce2)

        // Then
        assertNotEquals(hash1, hash2, "Разные nonce должны давать разные хеши")
    }
}
