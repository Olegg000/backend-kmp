package com.example.demo.core.database.entity

import com.example.demo.core.database.PushPlatform
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
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
    name = "push_device_token",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_push_device_platform_token", columnNames = ["platform", "token"])
    ],
    indexes = [
        Index(name = "idx_push_device_user_active", columnList = "user_id,active"),
        Index(name = "idx_push_device_last_seen", columnList = "last_seen_at"),
    ]
)
class PushDeviceTokenEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(optional = false)
    var user: UserEntity,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var platform: PushPlatform,

    @Column(nullable = false, length = 1024)
    var token: String,

    @Column(nullable = false)
    var active: Boolean = true,

    @Column(name = "last_seen_at", nullable = false)
    var lastSeenAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "app_version", length = 64)
    var appVersion: String? = null,

    @Column(length = 32)
    var locale: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
)
