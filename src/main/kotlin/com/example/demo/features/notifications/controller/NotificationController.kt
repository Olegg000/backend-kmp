package com.example.demo.features.notifications.controller

import com.example.demo.features.notifications.dto.MarkReadBatchRequest
import com.example.demo.features.notifications.dto.NotificationPageDto
import com.example.demo.features.notifications.dto.RosterDeadlineStatusDto
import com.example.demo.features.notifications.dto.PushTokenRegisterRequest
import com.example.demo.features.notifications.dto.PushTokenUnregisterRequest
import com.example.demo.features.notifications.dto.PushSettingsResponse
import com.example.demo.features.notifications.dto.UpdatePushSettingsRequest
import com.example.demo.features.notifications.service.NotificationService
import com.example.demo.features.notifications.service.PushDispatchService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
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
    private val notificationService: NotificationService,
    private val pushDispatchService: PushDispatchService,
) {

    @GetMapping("/roster-deadline")
    @PreAuthorize("hasAnyRole('CURATOR','ADMIN')")
    @Operation(summary = "Проверить статус дедлайна табеля")
    fun checkRosterDeadline(principal: Principal): RosterDeadlineStatusDto {
        return notificationService.checkCuratorRosterStatus(principal.name)
    }

    @GetMapping
    @Operation(summary = "Получить страницу уведомлений")
    fun getMyNotifications(
        principal: Principal,
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(required = false, defaultValue = "30") limit: Int
    ): ResponseEntity<NotificationPageDto> {
        return ResponseEntity.ok(notificationService.getUserNotificationsPage(principal.name, cursor, limit.coerceIn(1, 100)))
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

    @PostMapping("/read-batch")
    @Operation(summary = "Пакетно отметить уведомления как прочитанные")
    fun markAsReadBatch(
        principal: Principal,
        @RequestBody request: MarkReadBatchRequest
    ): ResponseEntity<Void> {
        notificationService.markAsReadBatch(principal.name, request.ids)
        return ResponseEntity.ok().build()
    }

    @PostMapping("/push/register")
    @Operation(summary = "Зарегистрировать push-токен устройства")
    fun registerPushToken(
        principal: Principal,
        @Valid @RequestBody request: PushTokenRegisterRequest,
    ): ResponseEntity<Void> {
        pushDispatchService.registerToken(principal.name, request)
        return ResponseEntity.ok().build()
    }

    @PostMapping("/push/unregister")
    @Operation(summary = "Отключить push-токен устройства")
    fun unregisterPushToken(
        principal: Principal,
        @Valid @RequestBody request: PushTokenUnregisterRequest,
    ): ResponseEntity<Void> {
        pushDispatchService.unregisterToken(principal.name, request.token)
        return ResponseEntity.ok().build()
    }

    @GetMapping("/push/settings")
    @Operation(summary = "Получить настройки push-уведомлений")
    fun getPushSettings(principal: Principal): ResponseEntity<PushSettingsResponse> {
        return ResponseEntity.ok(pushDispatchService.getSettings(principal.name))
    }

    @PatchMapping("/push/settings")
    @Operation(summary = "Обновить настройки push-уведомлений")
    fun updatePushSettings(
        principal: Principal,
        @Valid @RequestBody request: UpdatePushSettingsRequest,
    ): ResponseEntity<PushSettingsResponse> {
        return ResponseEntity.ok(pushDispatchService.updateSettings(principal.name, request.pushEnabled))
    }
}
