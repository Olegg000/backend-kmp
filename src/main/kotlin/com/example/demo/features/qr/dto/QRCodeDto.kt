package com.example.demo.features.qr.dto

import com.example.demo.core.database.MealType
import java.util.UUID

/**
 * Данные QR-кода (что кодируется в JSON и превращается в QR-изображение)
 *
 * ВАЖНО: nonce защищает от replay-атак (один и тот же QR нельзя использовать дважды)
 */
data class QRPayload(
    val userId: UUID,
    val timestamp: Long,        // Unix timestamp (округленный до 30 сек)
    val mealType: MealType,
    val nonce: String,          // Случайная строка (16 байт)
    val signature: String       // Base64 подпись всех полей выше
)

/**
 * Запрос на валидацию QR (повар сканирует)
 */
data class ValidateQRRequest(
    val userId: UUID,
    val timestamp: Long,
    val mealType: MealType,
    val nonce: String,
    val signature: String
)

/**
 * Ответ на валидацию
 */
data class ValidateQRResponse(
    val isValid: Boolean,
    val studentName: String?,
    val groupName: String? = null,
    val errorMessage: String? = null,
    val errorCode: String? = null  // Для клиента (чтобы показать конкретную ошибку)
)

/**
 * Коды ошибок валидации
 */
enum class QRValidationError(val code: String, val message: String) {
    EXPIRED("QR_EXPIRED", "QR-код устарел"),
    INVALID_SIGNATURE("INVALID_SIG", "Недействительная подпись"),
    ALREADY_USED("ALREADY_USED", "QR-код уже использован"),
    NO_PERMISSION("NO_PERMISSION", "Нет разрешения в табеле"),
    STUDENT_NOT_FOUND("NOT_FOUND", "Студент не найден"),
    ALREADY_ATE("ALREADY_ATE", "Студент уже получил это питание сегодня")
}