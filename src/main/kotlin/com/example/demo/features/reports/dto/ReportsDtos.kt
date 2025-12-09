package com.example.demo.features.reports.dto

import java.time.LocalDate

data class DailyReportResponse(
    val date: LocalDate,
    val breakfastCount: Long,
    val lunchCount: Long,
    val dinnerCount: Long,
    val snackCount: Long,
    val specialCount: Long,
    val totalCount: Long,
    val offlineTransactions: Long // Сколько было оффлайн-транзакций (для контроля)
)

data class GroupReportResponse(
    val groupName: String,
    val totalStudents: Int,
    val studentsWhoAte: Int,
    val percentage: Double
)