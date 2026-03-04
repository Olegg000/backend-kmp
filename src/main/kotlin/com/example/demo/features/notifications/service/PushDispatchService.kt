package com.example.demo.features.notifications.service

import com.example.demo.core.database.PushPlatform
import com.example.demo.core.database.entity.PushDeviceTokenEntity
import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.entity.UserNotificationSettingsEntity
import com.example.demo.core.database.repository.PushDeviceTokenRepository
import com.example.demo.core.database.repository.UserNotificationSettingsRepository
import com.example.demo.core.database.repository.UserRepository
import com.example.demo.core.exception.BusinessException
import com.example.demo.features.notifications.dto.PushSettingsResponse
import com.example.demo.features.notifications.dto.PushTokenRegisterRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class PushDispatchService(
    private val userRepository: UserRepository,
    private val pushDeviceTokenRepository: PushDeviceTokenRepository,
    private val userNotificationSettingsRepository: UserNotificationSettingsRepository,
    private val firebasePushGateway: FirebasePushGateway,
) {
    @Transactional
    fun registerToken(login: String, request: PushTokenRegisterRequest) {
        val user = requireUser(login)
        val token = request.token.trim()
        if (token.isBlank()) {
            throw BusinessException(
                code = "PUSH_TOKEN_REQUIRED",
                userMessage = "Пустой push-токен",
                status = HttpStatus.BAD_REQUEST,
            )
        }

        val platform = parsePlatform(request.platform)
        ensureSettingsExists(user)

        val now = LocalDateTime.now()
        val existing = pushDeviceTokenRepository.findByPlatformAndToken(platform, token)
        if (existing != null) {
            existing.user = user
            existing.platform = platform
            existing.token = token
            existing.active = true
            existing.lastSeenAt = now
            existing.appVersion = request.appVersion?.trim()?.ifBlank { null }
            existing.locale = request.locale?.trim()?.ifBlank { null }
            existing.updatedAt = now
            pushDeviceTokenRepository.save(existing)
            return
        }

        pushDeviceTokenRepository.save(
            PushDeviceTokenEntity(
                user = user,
                platform = platform,
                token = token,
                active = true,
                lastSeenAt = now,
                appVersion = request.appVersion?.trim()?.ifBlank { null },
                locale = request.locale?.trim()?.ifBlank { null },
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    @Transactional
    fun unregisterToken(login: String, token: String) {
        val user = requireUser(login)
        val normalized = token.trim()
        if (normalized.isBlank()) return

        pushDeviceTokenRepository.findAllByUserAndActiveTrue(user)
            .filter { it.token == normalized }
            .forEach { device ->
                device.active = false
                device.updatedAt = LocalDateTime.now()
                pushDeviceTokenRepository.save(device)
            }
    }

    @Transactional(readOnly = true)
    fun getSettings(login: String): PushSettingsResponse {
        val user = requireUser(login)
        val entity = userNotificationSettingsRepository.findByUser(user)
        return PushSettingsResponse(pushEnabled = entity?.pushEnabled ?: true)
    }

    @Transactional
    fun updateSettings(login: String, pushEnabled: Boolean): PushSettingsResponse {
        val user = requireUser(login)
        val entity = ensureSettingsExists(user)
        entity.pushEnabled = pushEnabled
        entity.updatedAt = LocalDateTime.now()
        userNotificationSettingsRepository.save(entity)
        return PushSettingsResponse(pushEnabled = entity.pushEnabled)
    }

    @Transactional
    fun dispatchToUser(
        user: UserEntity,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap(),
    ) {
        val settings = userNotificationSettingsRepository.findByUser(user)
        if (settings?.pushEnabled == false) return

        val tokens = pushDeviceTokenRepository.findAllByUserAndActiveTrue(user)
        if (tokens.isEmpty()) return

        val now = LocalDateTime.now()
        tokens.forEach { device ->
            val result = firebasePushGateway.sendToToken(
                token = device.token,
                title = title,
                body = body,
                data = data,
            )
            when (result) {
                PushDeliveryResult.SUCCESS -> {
                    device.lastSeenAt = now
                    device.updatedAt = now
                    pushDeviceTokenRepository.save(device)
                }
                PushDeliveryResult.INVALID_TOKEN -> {
                    device.active = false
                    device.updatedAt = now
                    pushDeviceTokenRepository.save(device)
                }
                PushDeliveryResult.FAILED -> {
                    // Не обновляем last_seen_at при фактической ошибке доставки.
                }
            }
        }
    }

    private fun ensureSettingsExists(user: UserEntity): UserNotificationSettingsEntity {
        return userNotificationSettingsRepository.findByUser(user)
            ?: userNotificationSettingsRepository.save(
                UserNotificationSettingsEntity(
                    user = user,
                    pushEnabled = true,
                    updatedAt = LocalDateTime.now(),
                )
            )
    }

    private fun parsePlatform(value: String): PushPlatform {
        return runCatching { PushPlatform.valueOf(value.trim().uppercase()) }.getOrElse {
            throw BusinessException(
                code = "PUSH_PLATFORM_INVALID",
                userMessage = "Неизвестная платформа push",
                status = HttpStatus.BAD_REQUEST,
            )
        }
    }

    private fun requireUser(login: String): UserEntity {
        return userRepository.findByLogin(login)
            ?: throw BusinessException(
                code = "USER_NOT_FOUND",
                userMessage = "Пользователь не найден",
                status = HttpStatus.NOT_FOUND,
            )
    }
}
