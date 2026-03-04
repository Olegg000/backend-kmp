package com.example.demo.features.qr.dto

import com.example.demo.core.database.MealType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class OfflineTransactionDto(
    @field:NotBlank
    @field:Size(min = 36, max = 36)
    val userId: String,

    @field:NotNull
    val timestamp: Long,

    @field:NotNull
    val mealType: MealType,

    @field:NotBlank
    @field:Size(min = 8, max = 160)
    val nonce: String,

    @field:NotBlank
    @field:Size(min = 32, max = 2048)
    val signature: String
)

data class SyncResponse(
    val successCount: Int,
    val failedCount: Int,
    val errors: List<SyncError>
)

data class SyncError(
    val userId: String,
    val reason: String,
    val transactionHash: String? = null,
)
