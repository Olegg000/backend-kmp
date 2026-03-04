package com.example.demo.features.qr.dto

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * Публичный ключ студента для оффлайн-верификации (скачивается поваром)
 */
data class StudentKeyDto(
    val userId: UUID,
    val publicKey: String,
    val name: String,
    val surname: String,
    val fatherName: String,
    val groupName: String?
)

/**
 * Разрешение на питание студента на конкретную дату (скачивается поваром)
 */
data class StudentPermissionDto(
    val studentId: UUID,
    val name: String,
    val surname: String,
    val breakfast: Boolean,
    val lunch: Boolean
)

data class ChefWeeklyReportDayDto(
    val date: LocalDate,
    val breakfastCount: Int,
    val lunchCount: Int,
    val bothCount: Int,
)

data class ChefWeeklyReportDto(
    val weekStart: LocalDate,
    val days: List<ChefWeeklyReportDayDto>,
    val totalBreakfastCount: Int,
    val totalLunchCount: Int,
    val totalBothCount: Int,
    val confirmed: Boolean,
    val confirmedAt: LocalDateTime? = null,
    val canConfirmNow: Boolean,
    val confirmWindowStart: LocalDateTime,
    val confirmWindowEnd: LocalDateTime,
    val confirmWindowHint: String,
)
