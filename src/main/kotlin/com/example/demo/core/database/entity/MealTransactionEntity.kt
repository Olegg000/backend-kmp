package com.example.demo.core.database.entity

import com.example.demo.core.database.MealType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "meal_transaction")
class MealTransactionEntity (

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int? = null,

    @Column(name = "transaction_hash", nullable = false)
    val transactionHash: String,

    @Column(name = "timestamp", nullable = false)
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