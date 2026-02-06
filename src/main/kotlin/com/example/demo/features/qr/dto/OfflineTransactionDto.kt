package com.example.demo.features.qr.dto

import com.example.demo.core.database.MealType

data class OfflineTransactionDto(
    val userId: String,
    val timestamp: Long,
    val mealType: MealType,
    val nonce: String,
    val signature: String
)

data class SyncResponse(
    val successCount: Int,
    val failedCount: Int,
    val errors: List<SyncError>
)

data class SyncError(
    val userId: String,
    val reason: String
)
