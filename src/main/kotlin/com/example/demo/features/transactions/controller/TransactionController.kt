package com.example.demo.features.transactions.controller

import com.example.demo.features.transactions.dto.SyncResponse
import com.example.demo.features.transactions.dto.TransactionSyncItem
import com.example.demo.features.transactions.service.TransactionsService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.security.Principal

@RestController
@RequestMapping("/api/v1/transactions")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Transactions", description = "Прием данных от повара")
class TransactionController(
    private val transactionService: TransactionsService
) {

    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('CHEF', 'ADMIN')")
    @Operation(summary = "Загрузить пачку транзакций (Синхронизация)")
    fun syncBatch(
        @RequestBody @Valid items: List<@Valid TransactionSyncItem>,
        principal: Principal // Получаем логин того, кто отправил
    ): SyncResponse {
        return transactionService.syncBatch(principal.name, items)
    }
}
