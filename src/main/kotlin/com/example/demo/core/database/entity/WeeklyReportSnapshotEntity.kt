package com.example.demo.core.database.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "weekly_report_snapshot",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_weekly_report_snapshot_week_date",
            columnNames = ["week_start", "report_date"]
        )
    ],
    indexes = [
        Index(name = "idx_weekly_report_snapshot_week", columnList = "week_start"),
    ]
)
class WeeklyReportSnapshotEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "week_start", nullable = false)
    val weekStart: LocalDate,

    @Column(name = "report_date", nullable = false)
    val date: LocalDate,

    @Column(name = "breakfast_count", nullable = false)
    var breakfastCount: Int,

    @Column(name = "lunch_count", nullable = false)
    var lunchCount: Int,

    @Column(name = "both_count", nullable = false)
    var bothCount: Int,

    @Column(name = "finalized_at", nullable = false)
    var finalizedAt: LocalDateTime,
)
