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
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "chef_week_confirmation",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_chef_week_confirmation_chef_week",
            columnNames = ["chef_id", "week_start"]
        )
    ],
    indexes = [
        Index(name = "idx_chef_week_confirmation_week", columnList = "week_start"),
    ]
)
class ChefWeekConfirmationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(optional = false)
    val chef: UserEntity,

    @Column(name = "week_start", nullable = false)
    val weekStart: LocalDate,

    @Column(name = "confirmed_at", nullable = false)
    var confirmedAt: LocalDateTime,
)
