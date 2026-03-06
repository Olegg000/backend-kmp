package com.example.demo.features.roster.dto

import com.example.demo.core.database.AccountStatus
import com.example.demo.core.database.NoMealReasonType
import com.example.demo.core.database.StudentCategory
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.util.UUID

// Строка табеля (Один студент)
data class StudentRosterRow(
    val studentId: UUID,
    val fullName: String,
    val studentCategory: StudentCategory?,
    val accountStatus: AccountStatus,
    val days: List<DayPermissionDto>
)

// Ячейка табеля (Один день)
data class DayPermissionDto(
    val date: LocalDate,
    val isBreakfast: Boolean,
    val isLunch: Boolean,
    val reason: String? = null,
    val noMealReasonType: NoMealReasonType? = null,
    val noMealReasonText: String? = null,
    val absenceFrom: LocalDate? = null,
    val absenceTo: LocalDate? = null,
    val comment: String? = null,
)

// Запрос от Куратора
data class UpdateRosterRequest(
    @field:NotNull
    val studentId: UUID,
    @field:Size(min = 1, max = 5)
    val permissions: List<@Valid DayPermissionDto>
)
