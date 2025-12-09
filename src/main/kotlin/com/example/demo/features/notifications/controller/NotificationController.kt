package com.example.demo.features.notifications.controller

import com.example.demo.features.notifications.service.NotificationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*
import java.security.Principal

@RestController
@RequestMapping("/api/v1/notifications")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Notifications", description = "Проверка необходимости уведомлений")
class NotificationController(
    private val notificationService: NotificationService
) {

    @GetMapping("/roster-deadline")
    @Operation(summary = "Проверить, заполнил ли куратор табель на следующую неделю")
    fun checkRosterDeadline(principal: Principal): Map<String, Any> {
        // Возвращает: { "needsReminder": true/false, "daysUntilDeadline": 2 }
        return notificationService.checkCuratorRosterStatus(principal.name)
    }
}