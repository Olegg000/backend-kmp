package com.example.demo.core.database.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "notifications")
class NotificationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    lateinit var user: UserEntity

    @Column(nullable = false)
    var title: String = ""

    @Column(nullable = false, length = 1000)
    var message: String = ""

    @Column(nullable = false)
    var isRead: Boolean = false

    @Column(nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
}
