package com.example.demo.features.reports.dto

import com.example.demo.core.database.MealType
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class SuspiciousTransactionDto(
    val id: Int,
    val date: LocalDate,
    val mealType: MealType,
    val studentId: UUID,
    val studentName: String,
    val groupName: String?,
    val chefId: UUID?,
    val chefName: String?,
    val reason: String,
    val attemptTimestamp: LocalDateTime,
    val resolved: Boolean
)