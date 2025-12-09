package com.example.demo.features.reports.controller

import com.example.demo.features.reports.dto.DailyReportResponse
import com.example.demo.features.reports.service.ReportsService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/reports")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Reports", description = "Отчетность для администрации")
class ReportsController(
    private val reportsService: ReportsService
) {

    @GetMapping("/daily")
    @PreAuthorize("hasAnyRole('ADMIN', 'REGISTRATOR')")
    @Operation(summary = "Отчет за день (сколько студентов поело)")
    fun getDailyReport(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate
    ): DailyReportResponse {
        return reportsService.generateDailyReport(date)
    }

    @GetMapping("/weekly")
    @PreAuthorize("hasAnyRole('ADMIN', 'REGISTRATOR')")
    @Operation(summary = "Отчет за неделю (по дням)")
    fun getWeeklyReport(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate
    ): List<DailyReportResponse> {
        return reportsService.generateWeeklyReport(startDate)
    }

    @GetMapping("/export/csv")
    @PreAuthorize("hasAnyRole('ADMIN', 'REGISTRATOR')")
    @Operation(summary = "Экспорт отчета в CSV")
    fun exportCSV(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate
    ): ResponseEntity<ByteArray> {
        val csv = reportsService.exportToCSV(startDate, endDate)

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report_${startDate}_${endDate}.csv")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(csv.toByteArray())
    }
}