package com.example.demo.features.reports.controller

import com.example.demo.core.database.MealType
import com.example.demo.core.database.repository.SuspiciousTransactionRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.features.reports.dto.SuspiciousTransactionDto
import com.example.demo.features.reports.service.FraudPdfService
import com.example.demo.features.reports.service.SuspiciousTransactionsService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.security.Principal
import java.time.LocalDate
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

@RestController
@RequestMapping("/api/v1/reports/fraud")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Fraud Reports", description = "Подозрительные транзакции")
class SuspiciousTransactionsController(
    private val service: SuspiciousTransactionsService,
    private val suspiciousRepo: SuspiciousTransactionRepository,
    private val userRepository: UserRepository,
    private val fraudPdfService: FraudPdfService
) {

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'REGISTRATOR')")
    @Operation(summary = "Получить список подозрительных транзакций")
    fun getSuspicious(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate,
        @RequestParam(required = false) mealType: MealType?,
        @RequestParam(required = false) resolved: Boolean?
    ): List<SuspiciousTransactionDto> {
        return service.getSuspicious(startDate, endDate, mealType, resolved)
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('ADMIN', 'REGISTRATOR')")
    @Operation(summary = "Пометить подозрительную транзакцию как проверенную")
    fun resolve(@PathVariable id: Int, principal: Principal): SuspiciousTransactionDto {
        val entity = suspiciousRepo.findById(id)
            .orElseThrow { RuntimeException("Запись не найдена") }

        val resolver = userRepository.findByLogin(principal.name)
            ?: throw RuntimeException("Пользователь не найден")

        entity.resolved = true
        entity.resolvedBy = resolver
        entity.resolvedAt = java.time.LocalDateTime.now()
        val saved = suspiciousRepo.save(entity)

        return service.getSuspicious(saved.date, saved.date)
            .first { it.id == saved.id }
    }

    @GetMapping("/export/csv")
    @PreAuthorize("hasAnyRole('ADMIN', 'REGISTRATOR')")
    @Operation(summary = "Экспорт подозрительных транзакций в CSV")
    fun exportCsv(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate,
        @RequestParam(required = false) mealType: MealType?,
        @RequestParam(required = false) resolved: Boolean?
    ): ResponseEntity<ByteArray> {
        val csv = service.exportToCsv(startDate, endDate, mealType, resolved)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=fraud_${startDate}_${endDate}.csv")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(csv.toByteArray(Charsets.UTF_8))
    }

    @GetMapping("/export/pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'REGISTRATOR')")
    @Operation(summary = "Экспорт подозрительных транзакций в PDF")
    fun exportPdf(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate
    ): ResponseEntity<ByteArray> {
        val pdf = fraudPdfService.generatePdf(startDate, endDate)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=fraud_${startDate}_${endDate}.pdf")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf)
    }
}