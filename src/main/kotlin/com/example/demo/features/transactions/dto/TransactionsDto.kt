package com.example.demo.features.transactions.dto

import com.example.demo.core.database.MealType
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDateTime
import java.util.UUID

// Одна запись о питании (с телефона повара)
data class TransactionSyncItem(
    @field:NotNull
    val studentId: UUID,

    @field:NotNull
    val mealType: MealType,       // Что ел (определяет повар кнопкой или по времени)

    @field:Size(max = 255)
    val transactionHash: String? = null, // Хэш QR-кода (для защиты от повторов)

    @Deprecated("Use timestampEpochSec")
    val timestamp: LocalDateTime? = null,

    val timestampEpochSec: Long? = null,
)

// Ответ сервера: сколько успешно, сколько ошибок
data class SyncResponse(
    val successCount: Int,
    val errors: List<String>,
    val processed: List<TransactionSyncProcessedItem> = emptyList(),
)

data class TransactionSyncProcessedItem(
    val transactionHash: String?,
    val studentId: UUID,
    val status: String,
    val code: String? = null,
    val message: String? = null,
)
