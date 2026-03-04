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
    name = "curator_week_submission",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_curator_week_submission_curator_week",
            columnNames = ["curator_id", "week_start"]
        )
    ],
    indexes = [
        Index(name = "idx_curator_week_submission_week", columnList = "week_start"),
    ]
)
class CuratorWeekSubmissionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(optional = false)
    val curator: UserEntity,

    @Column(name = "week_start", nullable = false)
    val weekStart: LocalDate,

    @Column(name = "submitted_at", nullable = false)
    var submittedAt: LocalDateTime,

    @Column(name = "last_updated_at", nullable = false)
    var lastUpdatedAt: LocalDateTime,
)
