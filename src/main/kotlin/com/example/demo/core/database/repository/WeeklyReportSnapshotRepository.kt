package com.example.demo.core.database.repository

import com.example.demo.core.database.entity.WeeklyReportSnapshotEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface WeeklyReportSnapshotRepository : JpaRepository<WeeklyReportSnapshotEntity, Long> {
    fun findAllByWeekStartOrderByDateAsc(weekStart: LocalDate): List<WeeklyReportSnapshotEntity>
    fun deleteAllByWeekStart(weekStart: LocalDate)
}
