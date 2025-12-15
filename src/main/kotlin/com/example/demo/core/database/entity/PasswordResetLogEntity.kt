package com.example.demo.core.database.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "password_reset_log")
class PasswordResetLogEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(optional = false)
    val user: UserEntity,

    @ManyToOne
    val resetBy: UserEntity? = null,

    @Column(nullable = false)
    val timestamp: LocalDateTime = LocalDateTime.now()
)