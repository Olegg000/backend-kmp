package com.example.demo.features.reports.dto

import com.example.demo.core.database.CuratorWeekFillStatus
import com.example.demo.core.database.NoMealReasonType
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
    val lunchScannedByName: String?,
    val plannedBreakfast: Boolean,
    val plannedLunch: Boolean,
    val noMealReasonType: NoMealReasonType?,
    val noMealReasonText: String?,
    val absenceFrom: LocalDate?,
    val absenceTo: LocalDate?,
    val comment: String?,
    val isSyntheticMissingRoster: Boolean,
)

data class ConsumptionSummaryDay(
    val date: LocalDate,
    val breakfastCount: Int,
    val lunchCount: Int,
    val bothCount: Int,
)

data class ZeroFillCuratorSummary(
    val curatorId: UUID,
    val curatorName: String,
    val weekStart: LocalDate,
    val groupIds: List<Int>,
    val filledCells: Int,
    val expectedCells: Int,
    val fillStatus: CuratorWeekFillStatus,
)

data class ConsumptionSummaryResponse(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val days: List<ConsumptionSummaryDay>,
    val totalBreakfastCount: Int,
    val totalLunchCount: Int,
    val totalBothCount: Int,
    val missingRosterRowsCount: Int,
    val zeroFillCurators: List<ZeroFillCuratorSummary>,
)
