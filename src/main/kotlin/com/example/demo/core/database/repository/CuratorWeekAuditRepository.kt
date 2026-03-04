package com.example.demo.core.database.repository

import com.example.demo.core.database.entity.CuratorWeekAuditEntity
import com.example.demo.core.database.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface CuratorWeekAuditRepository : JpaRepository<CuratorWeekAuditEntity, Long> {
    fun findByCuratorAndWeekStart(curator: UserEntity, weekStart: LocalDate): CuratorWeekAuditEntity?
    fun findAllByWeekStart(weekStart: LocalDate): List<CuratorWeekAuditEntity>
    fun findAllByWeekStartBetween(weekStartFrom: LocalDate, weekStartTo: LocalDate): List<CuratorWeekAuditEntity>
}
