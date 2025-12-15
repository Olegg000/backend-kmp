package com.example.demo.core.database.repository

import com.example.demo.core.database.entity.PasswordResetLogEntity
import com.example.demo.core.database.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface PasswordResetLogRepository : JpaRepository<PasswordResetLogEntity, Long> {

    fun countByUserAndTimestampBetween(
        user: UserEntity,
        start: LocalDateTime,
        end: LocalDateTime
    ): Long
}