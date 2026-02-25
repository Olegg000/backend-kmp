package com.example.demo.features.notifications.controller

import com.example.demo.features.notifications.dto.NotificationDto
import com.example.demo.features.notifications.service.NotificationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.security.Principal

@RestController
@RequestMapping("/api/v1/notifications")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Notifications", description = "Уведомления")
class NotificationController(
    private val notificationService: NotificationService
) {

    @GetMapping("/roster-deadline")
    @Operation(summary = "Проверить, заполнил ли куратор табель на следующую неделю")
    fun checkRosterDeadline(principal: Principal): Map<String, Any> {
        return notificationService.checkCuratorRosterStatus(principal.name)
    }

    @GetMapping
    @Operation(summary = "Получить свои уведомления")
    fun getMyNotifications(principal: Principal): ResponseEntity<List<NotificationDto>> {
        return ResponseEntity.ok(notificationService.getUserNotifications(principal.name))
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Получить количество непрочитанных уведомлений")
    fun getUnreadCount(principal: Principal): ResponseEntity<Map<String, Long>> {
        return ResponseEntity.ok(mapOf("count" to notificationService.getUnreadCount(principal.name)))
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Отметить уведомление как прочитанное")
    fun markAsRead(@PathVariable id: Long, principal: Principal): ResponseEntity<Void> {
        notificationService.markAsRead(principal.name, id)
        return ResponseEntity.ok().build()
    }
}
