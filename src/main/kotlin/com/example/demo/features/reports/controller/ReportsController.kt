package com.example.demo.features.reports.controller

import com.example.demo.core.logging.maskLogin
import com.example.demo.features.reports.dto.AssignedByRoleFilter
import com.example.demo.features.reports.dto.ConsumptionReportRow
import com.example.demo.features.reports.service.ReportsPdfService
import com.example.demo.features.reports.service.ReportsService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.security.Principal
import java.time.LocalDate
import kotlin.math.roundToLong

@RestController
@RequestMapping("/api/v1/reports")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Reports", description = "Детальные отчёты по питанию")
class ReportsController(
    private val reportsService: ReportsService,
    private val reportsPdfService: ReportsPdfService
) {
    private val logger = LoggerFactory.getLogger(ReportsController::class.java)

    @GetMapping("/consumption")
    @PreAuthorize("hasAnyRole('ADMIN', 'CURATOR')")
    @Operation(summary = "Детальный отчёт по факту питания (студент × день)")
    fun getConsumptionReport(
        principal: Principal,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate,
        @RequestParam(required = false) groupId: Int?,
        @RequestParam(defaultValue = "ALL") assignedByRole: AssignedByRoleFilter
    ): List<ConsumptionReportRow> {
        val startedAtNanos = System.nanoTime()
        val loginMasked = maskLogin(principal.name)
        val roles = currentRoles()
        logger.info(
            "Consumption report request started: loginMasked={}, roles={}, startDate={}, endDate={}, groupId={}, assignedByRole={}",
            loginMasked,
            roles,
            startDate,
            endDate,
            groupId,
            assignedByRole
        )
        try {
            val rows = reportsService.generateConsumptionReport(
                currentLogin = principal.name,
                startDate = startDate,
                endDate = endDate,
                groupId = groupId,
                assignedByRoleFilter = assignedByRole
            )
            val durationMs = ((System.nanoTime() - startedAtNanos).toDouble() / 1_000_000.0).roundToLong()
            logger.info(
                "Consumption report request completed: loginMasked={}, roles={}, rowsCount={}, durationMs={}",
                loginMasked,
                roles,
                rows.size,
                durationMs
            )
            return rows
        } catch (e: Exception) {
            val durationMs = ((System.nanoTime() - startedAtNanos).toDouble() / 1_000_000.0).roundToLong()
            logger.error(
                "Consumption report request failed: loginMasked={}, roles={}, startDate={}, endDate={}, groupId={}, assignedByRole={}, durationMs={}",
                loginMasked,
                roles,
                startDate,
                endDate,
                groupId,
                assignedByRole,
                durationMs,
                e
            )
            throw e
        }
    }

    @GetMapping("/consumption/export/csv")
    @PreAuthorize("hasAnyRole('ADMIN', 'CURATOR')")
    @Operation(summary = "Экспорт детального отчёта по питанию в CSV")
    fun exportConsumptionCsv(
        principal: Principal,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate,
        @RequestParam(required = false) groupId: Int?,
        @RequestParam(defaultValue = "ALL") assignedByRole: AssignedByRoleFilter
    ): ResponseEntity<ByteArray> {
        logger.info(
            "Consumption CSV export started: loginMasked={}, roles={}, startDate={}, endDate={}, groupId={}, assignedByRole={}",
            maskLogin(principal.name),
            currentRoles(),
            startDate,
            endDate,
            groupId,
            assignedByRole
        )
        val csv = reportsService.exportToCsv(
            currentLogin = principal.name,
            startDate = startDate,
            endDate = endDate,
            groupId = groupId,
            assignedByRoleFilter = assignedByRole
        )
        logger.info(
            "Consumption CSV export completed: loginMasked={}, bytes={}",
            maskLogin(principal.name),
            csv.toByteArray().size
        )

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=consumption_${startDate}_${endDate}.csv")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(csv.toByteArray())
    }

    @GetMapping("/consumption/export/pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'CURATOR')")
    @Operation(summary = "Экспорт детального отчёта по питанию в PDF")
    fun exportConsumptionPdf(
        principal: Principal,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate,
        @RequestParam(required = false) groupId: Int?,
        @RequestParam(defaultValue = "ALL") assignedByRole: AssignedByRoleFilter
    ): ResponseEntity<ByteArray> {
        logger.info(
            "Consumption PDF export started: loginMasked={}, roles={}, startDate={}, endDate={}, groupId={}, assignedByRole={}",
            maskLogin(principal.name),
            currentRoles(),
            startDate,
            endDate,
            groupId,
            assignedByRole
        )
        val pdf = reportsPdfService.generateConsumptionPdf(
            currentLogin = principal.name,
            startDate = startDate,
            endDate = endDate,
            groupId = groupId,
            assignedByRoleFilter = assignedByRole
        )
        logger.info(
            "Consumption PDF export completed: loginMasked={}, bytes={}",
            maskLogin(principal.name),
            pdf.size
        )

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=consumption_${startDate}_${endDate}.pdf")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf)
    }

    private fun currentRoles(): String {
        val authorities = SecurityContextHolder.getContext()
            .authentication
            ?.authorities
            ?.map { it.authority }
            .orEmpty()
        if (authorities.isEmpty()) return "unknown"
        return authorities.sorted().joinToString(",")
    }
}
