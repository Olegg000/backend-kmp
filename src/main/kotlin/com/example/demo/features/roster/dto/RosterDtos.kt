package com.example.demo.features.roster.dto

import java.time.LocalDate
import java.util.UUID

// Строка табеля (Один студент)
data class StudentRosterRow(
    val studentId: UUID,
    val fullName: String,
    val days: List<DayPermissionDto>
)

// Ячейка табеля (Один день)
data class DayPermissionDto(
    val date: LocalDate,
    val isBreakfast: Boolean,
    val isLunch: Boolean,
    val reason: String? = null // Причина (если не ест)
)

// Запрос от Куратора
data class UpdateRosterRequest(
    val studentId: UUID,
    val permissions: List<DayPermissionDto>
)
