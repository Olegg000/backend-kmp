package com.example.demo.core.database.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

@Entity
@Table(
    name = "notification_dispatch_log",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_notification_dispatch_user_type_bucket",
            columnNames = ["user_id", "dispatch_type", "bucket_key"]
        )
    ],
    indexes = [
        Index(name = "idx_notification_dispatch_user_type", columnList = "user_id,dispatch_type"),
    ]
)
class NotificationDispatchLogEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(optional = false)
    val user: UserEntity,

    @Column(name = "dispatch_type", nullable = false, length = 120)
    val type: String,

    @Column(name = "bucket_key", nullable = false, length = 120)
    val bucketKey: String,

    @Column(name = "sent_at", nullable = false)
    var sentAt: LocalDateTime = LocalDateTime.now(),
)
