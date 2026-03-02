package com.example.demo.features.student.dto

import com.example.demo.features.roster.dto.DayPermissionDto
import java.time.LocalDate
import java.util.UUID

data class StudentSelfRosterDto(
    val studentId: UUID,
    val fullName: String,
    val groupName: String?,
    val weekStart: LocalDate,
    val days: List<DayPermissionDto>
)

data class StudentTodayMealsDto(
    val date: LocalDate,
    val isBreakfastAllowed: Boolean,
    val isLunchAllowed: Boolean,
    val reason: String?,
    // Добавлено: информация о том, какие приемы пищи уже использованы
    val isBreakfastConsumed: Boolean = false,
    val isLunchConsumed: Boolean = false
)
