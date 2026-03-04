package com.example.demo.core.database.repository

import com.example.demo.core.database.entity.UserEntity
import com.example.demo.core.database.entity.UserNotificationSettingsEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserNotificationSettingsRepository : JpaRepository<UserNotificationSettingsEntity, UUID> {
    fun findByUser(user: UserEntity): UserNotificationSettingsEntity?
}
