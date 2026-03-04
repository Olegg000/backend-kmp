package com.example.demo.core.database.entity

import com.example.demo.core.database.MealType
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
    name = "meal_transaction",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_meal_transaction_hash", columnNames = ["transaction_hash"])
    ],
    indexes = [
        Index(name = "idx_meal_tx_student_time_meal", columnList = "student_id,meal_timestamp,meal_type"),
        Index(name = "idx_meal_tx_hash", columnList = "transaction_hash"),
    ]
)
class MealTransactionEntity (

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int? = null,

    @Column(name = "transaction_hash", nullable = false, unique = true)
    val transactionHash: String,

    @Column(name = "meal_timestamp", nullable = false)
    val timeStamp: LocalDateTime,

    @ManyToOne
    val student: UserEntity,

    @ManyToOne
    val chef: UserEntity,

    @Column(name = "offline", nullable = false)
    val isOffline: Boolean = false,

    @Column(name = "meal_type", nullable = false)
    val mealType: MealType
)
