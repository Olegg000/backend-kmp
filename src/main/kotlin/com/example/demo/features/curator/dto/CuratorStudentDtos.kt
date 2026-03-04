package com.example.demo.features.curator.dto

import com.example.demo.core.database.NoMealReasonType
import com.example.demo.core.database.StudentCategory
import java.time.LocalDate
import java.util.UUID

data class CuratorStudentCategoryUpdateRequest(
    val studentCategory: StudentCategory
)

data class CuratorStudentRow(
    val userId: UUID,
    val fullName: String,
    val groupId: Int,
    val groupName: String,
    val studentCategory: StudentCategory?
)

data class CuratorStudentAbsenceRequest(
    val noMealReasonType: NoMealReasonType,
    val noMealReasonText: String? = null,
    val absenceFrom: LocalDate,
    val absenceTo: LocalDate,
    val comment: String? = null,
)
