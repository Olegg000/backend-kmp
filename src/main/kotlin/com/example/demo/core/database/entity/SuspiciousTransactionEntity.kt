package com.example.demo.core.database.entity

import com.example.demo.core.database.MealType
import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "suspicious_transaction")
class SuspiciousTransactionEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int? = null,

    @ManyToOne(optional = false)
    val student: UserEntity,

    @ManyToOne
    val chef: UserEntity? = null,

    @Column(nullable = false)
    val date: LocalDate,

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", nullable = false)
    val mealType: MealType,

    @Column(nullable = false)
    val reason: String,

    @Column(name = "base_tx_hash")
    val baseTransactionHash: String? = null,

    @Column(name = "attempt_tx_hash")
    val attemptTransactionHash: String? = null,

    @Column(name = "attempt_timestamp", nullable = false)
    val attemptTimestamp: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var resolved: Boolean = false,

    @ManyToOne
    var resolvedBy: UserEntity? = null,

    var resolvedAt: LocalDateTime? = null
)