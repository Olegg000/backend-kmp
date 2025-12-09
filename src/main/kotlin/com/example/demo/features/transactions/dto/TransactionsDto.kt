package com.example.demo.features.transactions.dto

import com.example.demo.core.database.MealType
import java.time.LocalDateTime
import java.util.UUID

// Одна запись о питании (с телефона повара)
data class TransactionSyncItem(
    val studentId: UUID,
    val timestamp: LocalDateTime, // Время, когда был скан (может быть в прошлом)
    val mealType: MealType,       // Что ел (определяет повар кнопкой или по времени)
    val transactionHash: String? = null // Хэш QR-кода (для защиты от повторов)
)

// Ответ сервера: сколько успешно, сколько ошибок
data class SyncResponse(
    val successCount: Int,
    val errors: List<String>
)