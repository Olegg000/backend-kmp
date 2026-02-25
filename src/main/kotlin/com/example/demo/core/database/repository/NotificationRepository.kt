package com.example.demo.core.database.repository

import com.example.demo.core.database.entity.NotificationEntity
import com.example.demo.core.database.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface NotificationRepository : JpaRepository<NotificationEntity, Long> {
    fun findAllByUserOrderByCreatedAtDesc(user: UserEntity): List<NotificationEntity>
    fun countByUserAndIsReadFalse(user: UserEntity): Long
}
