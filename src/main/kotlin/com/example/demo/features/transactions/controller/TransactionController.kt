package com.example.demo.features.transactions.controller

import com.example.demo.core.exception.BusinessException
import com.example.demo.features.transactions.dto.SyncResponse
import com.example.demo.features.transactions.dto.TransactionSyncItem
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.security.Principal

@RestController
@RequestMapping("/api/v1/transactions")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Transactions", description = "Прием данных от повара")
class TransactionController {
    private val logger = LoggerFactory.getLogger(TransactionController::class.java)

    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('CHEF', 'ADMIN')")
    @Operation(summary = "Загрузить пачку транзакций (Синхронизация)")
    fun syncBatch(
        @RequestBody @Valid items: List<@Valid TransactionSyncItem>,
        principal: Principal // Получаем логин того, кто отправил
    ): SyncResponse {
        logger.warn(
            "Legacy batch sync attempt blocked: login={}, itemsCount={}",
            principal.name,
            items.size,
        )
        throw BusinessException(
            code = "BATCH_SYNC_DISABLED",
            userMessage = "Старый пакетный sync отключен. Обновите приложение.",
            status = HttpStatus.GONE,
        )
    }
}
