package com.example.demo.core.database.repository

import com.example.demo.core.database.entity.NotificationDispatchLogEntity
import com.example.demo.core.database.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository

interface NotificationDispatchLogRepository : JpaRepository<NotificationDispatchLogEntity, Long> {
    fun existsByUserAndTypeAndBucketKey(user: UserEntity, type: String, bucketKey: String): Boolean
}
