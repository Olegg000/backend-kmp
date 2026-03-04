package com.example.demo.core.database.entity

import com.example.demo.core.database.CuratorWeekFillStatus
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
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "curator_week_audit",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_curator_week_audit_curator_week",
            columnNames = ["curator_id", "week_start"]
        )
    ],
    indexes = [
        Index(name = "idx_curator_week_audit_week", columnList = "week_start"),
    ]
)
class CuratorWeekAuditEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(optional = false)
    val curator: UserEntity,

    @Column(name = "week_start", nullable = false)
    val weekStart: LocalDate,

    @Column(name = "filled_cells", nullable = false)
    var filledCells: Int,

    @Column(name = "expected_cells", nullable = false)
    var expectedCells: Int,

    @Enumerated(EnumType.STRING)
    @Column(name = "fill_status", nullable = false)
    var fillStatus: CuratorWeekFillStatus,

    @Column(name = "locked_at", nullable = false)
    var lockedAt: LocalDateTime,
)
