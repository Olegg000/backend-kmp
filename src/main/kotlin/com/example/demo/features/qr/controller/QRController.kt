package com.example.demo.features.qr.controller

import com.example.demo.features.qr.dto.ValidateQRRequest
import com.example.demo.features.qr.dto.ValidateQRResponse
import com.example.demo.features.qr.dto.OfflineTransactionDto
import com.example.demo.features.qr.dto.SyncResponse
import com.example.demo.features.qr.service.QRValidationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/qr")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "QR Validation", description = "Валидация QR-кодов")
class QRController(
    private val qrValidationService: QRValidationService
) {

    @PostMapping("/validate")
    @PreAuthorize("hasAnyRole('CHEF', 'ADMIN')")
    @Operation(summary = "Проверить QR-код (онлайн)")
    fun validateOnline(@RequestBody @Valid req: ValidateQRRequest): ValidateQRResponse {
        return qrValidationService.validateOnline(req)
    }

    @PostMapping("/validate-offline")
    @Operation(summary = "Проверить QR-код (оффлайн, без JWT)")
    fun validateOffline(@RequestBody @Valid req: ValidateQRRequest): ValidateQRResponse {
        return qrValidationService.validateOffline(req)
    }

    @PostMapping("/sync")
    @PreAuthorize("hasAnyRole('CHEF', 'ADMIN')")
    @Operation(summary = "Синхронизация оффлайн транзакций")
    fun syncTransactions(@RequestBody @Valid transactions: List<@Valid OfflineTransactionDto>): SyncResponse {
        return qrValidationService.syncOfflineTransactions(transactions)
    }
}
