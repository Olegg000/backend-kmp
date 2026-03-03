package com.example.demo.features.reports.dto

import com.example.demo.core.database.StudentCategory
import java.time.LocalDate
import java.util.UUID

enum class AssignedByRoleFilter {
    ALL,
    ADMIN,
    CURATOR
}

enum class AssignedByRole {
    ADMIN,
    CURATOR
}

data class ConsumptionReportRow(
    val date: LocalDate,
    val groupId: Int,
    val groupName: String,
    val studentId: UUID,
    val studentName: String,
    val category: StudentCategory?,
    val assignedByRole: AssignedByRole?,
    val assignedByName: String?,
    val breakfastUsed: Boolean,
    val breakfastTransactionId: Int?,
    val breakfastScannedByName: String?,
    val lunchUsed: Boolean,
    val lunchTransactionId: Int?,
    val lunchScannedByName: String?
)
