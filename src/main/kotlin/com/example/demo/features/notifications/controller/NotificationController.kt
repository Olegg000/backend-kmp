package com.example.demo.features.notifications.controller

import com.example.demo.features.notifications.dto.MarkReadBatchRequest
import com.example.demo.features.notifications.dto.NotificationPageDto
import com.example.demo.features.notifications.service.NotificationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@RestController
@RequestMapping("/api/v1/notifications")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Notifications", description = "User notifications")
class NotificationController(
    private val notificationService: NotificationService
) {

    @GetMapping("/roster-deadline")
    @Operation(summary = "Check curator roster deadline status")
    fun checkRosterDeadline(principal: Principal): Map<String, Any> {
        return notificationService.checkCuratorRosterStatus(principal.name)
    }

    @GetMapping
    @Operation(summary = "Get notifications page")
    fun getMyNotifications(
        principal: Principal,
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(required = false, defaultValue = "30") limit: Int
    ): ResponseEntity<NotificationPageDto> {
        return ResponseEntity.ok(notificationService.getUserNotificationsPage(principal.name, cursor, limit.coerceIn(1, 100)))
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get unread notification count")
    fun getUnreadCount(principal: Principal): ResponseEntity<Map<String, Long>> {
        return ResponseEntity.ok(mapOf("count" to notificationService.getUnreadCount(principal.name)))
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark notification as read")
    fun markAsRead(@PathVariable id: Long, principal: Principal): ResponseEntity<Void> {
        notificationService.markAsRead(principal.name, id)
        return ResponseEntity.ok().build()
    }

    @PostMapping("/read-batch")
    @Operation(summary = "Mark notifications as read in batch")
    fun markAsReadBatch(
        principal: Principal,
        @RequestBody request: MarkReadBatchRequest
    ): ResponseEntity<Void> {
        notificationService.markAsReadBatch(principal.name, request.ids)
        return ResponseEntity.ok().build()
    }
}

