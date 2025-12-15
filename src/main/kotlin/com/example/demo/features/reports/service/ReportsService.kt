package com.example.demo.features.reports.service

import com.example.demo.core.database.MealType
import com.example.demo.core.database.repository.MealTransactionRepository
import com.example.demo.features.reports.dto.DailyReportResponse
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalTime
import com.example.demo.core.database.Role

@Service
class ReportsService(
    private val transactionRepository: MealTransactionRepository
) {

    fun generateDailyReport(date: LocalDate): DailyReportResponse {
        val startOfDay = date.atStartOfDay()
        val endOfDay = date.atTime(LocalTime.MAX)

        val transactions = transactionRepository.findAllByTimeStampBetween(startOfDay, endOfDay)

        return DailyReportResponse(
            date = date,
            breakfastCount = transactionRepository.countUniqueStudentsByMealTypeAndDate(
                MealType.BREAKFAST, startOfDay, endOfDay
            ),
            lunchCount = transactionRepository.countUniqueStudentsByMealTypeAndDate(
                MealType.LUNCH, startOfDay, endOfDay
            ),
            dinnerCount = transactionRepository.countUniqueStudentsByMealTypeAndDate(
                MealType.DINNER, startOfDay, endOfDay
            ),
            snackCount = transactionRepository.countUniqueStudentsByMealTypeAndDate(
                MealType.SNACK, startOfDay, endOfDay
            ),
            specialCount = transactionRepository.countUniqueStudentsByMealTypeAndDate(
                MealType.SPECIAL, startOfDay, endOfDay
            ),
            totalCount = transactions.distinctBy { it.student.id }.size.toLong(),
            offlineTransactions = transactions.count { it.isOffline }.toLong()
        )
    }

    fun generateWeeklyReport(startDate: LocalDate): List<DailyReportResponse> {
        return (0..6).map { day ->
            val date = startDate.plusDays(day.toLong())
            generateDailyReport(date)
        }
    }

    fun exportToCSV(startDate: LocalDate, endDate: LocalDate): String {
        val header = "Дата,Завтрак,Обед,Ужин,Полдник,Спец.питание,Всего,Оффлайн\n"

        var currentDate = startDate
        val rows = mutableListOf<String>()

        while (!currentDate.isAfter(endDate)) {
            val report = generateDailyReport(currentDate)
            rows.add("${report.date},${report.breakfastCount},${report.lunchCount},${report.dinnerCount},${report.snackCount},${report.specialCount},${report.totalCount},${report.offlineTransactions}")
            currentDate = currentDate.plusDays(1)
        }

        return header + rows.joinToString("\n")
    }
}