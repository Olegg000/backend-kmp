package com.example.demo.core.database.repository

import com.example.demo.core.database.entity.CuratorWeekSubmissionEntity
import com.example.demo.core.database.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface CuratorWeekSubmissionRepository : JpaRepository<CuratorWeekSubmissionEntity, Long> {
    fun findByCuratorAndWeekStart(curator: UserEntity, weekStart: LocalDate): CuratorWeekSubmissionEntity?
}
