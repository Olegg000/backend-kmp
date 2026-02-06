package com.example.demo.features.qr.service

import com.example.demo.core.database.MealType
import com.example.demo.core.util.CryptoUtils
import org.springframework.stereotype.Service
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*

@Service
class QRCodeService {

    /**
     * Генерация подписи для QR-кода
     * Вызывается на клиенте (студент), но логика дублируется для тестирования
     */
    fun generateSignature(
        userId: String,
        timestamp: Long,
        mealType: MealType,
        nonce: String,
        privateKeyBase64: String
    ): String {
        // Формат данных для подписи: userId:timestamp:mealType:nonce
        val data = "$userId:$timestamp:${mealType.name}:$nonce"
        val privateKey = decodePrivateKey(privateKeyBase64)

        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(data.toByteArray())

        return Base64.getEncoder().encodeToString(signature.sign())
    }

    /**
     * Проверка подписи QR-кода (повар проверяет offline)
     * КРИТИЧЕСКИ ВАЖНО: Это основная защита от подделки QR
     */
    fun verifySignature(
        userId: String,
        timestamp: Long,
        mealType: MealType,
        nonce: String,
        signatureBase64: String,
        publicKeyBase64: String
    ): Boolean {
        return try {
            val data = "$userId:$timestamp:${mealType.name}:$nonce"
            val publicKey = decodePublicKey(publicKeyBase64)
            val signatureBytes = Base64.getDecoder().decode(signatureBase64)

            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initVerify(publicKey)
            signature.update(data.toByteArray())

            signature.verify(signatureBytes)
        } catch (e: Exception) {
            org.slf4j.LoggerFactory.getLogger(QRCodeService::class.java).error("Signature verification failed", e)
            false
        }
    }

    /**
     * Округление timestamp до 30 секунд (для окна валидности)
     * Это позволяет QR-коду быть валидным 30 секунд
     */
    fun roundTimestamp(timestamp: Long): Long {
        return (timestamp / 30) * 30
    }

    /**
     * Проверка, что timestamp находится в допустимом окне
     * Окно: ±60 секунд (2 интервала по 30 секунд)
     */
    fun isTimestampValid(timestamp: Long, currentTime: Long = System.currentTimeMillis() / 1000): Boolean {
        val diff = kotlin.math.abs(currentTime - timestamp)
        return diff <= 60
    }

    /**
     * Генерация хеша транзакции (для защиты от дублей)
     * Формат: SHA256(userId + timestamp + mealType + nonce)
     */
    fun generateTransactionHash(userId: String, timestamp: Long, mealType: MealType, nonce: String): String {
        return CryptoUtils.sha256("$userId:$timestamp:${mealType.name}:$nonce")
    }

    // === Вспомогательные методы ===

    private fun decodePrivateKey(base64: String): PrivateKey {
        val keyBytes = Base64.getDecoder().decode(base64)
        val spec = PKCS8EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePrivate(spec)
    }

    private fun decodePublicKey(base64: String): PublicKey {
        val keyBytes = Base64.getDecoder().decode(base64)
        val spec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePublic(spec)
    }
}