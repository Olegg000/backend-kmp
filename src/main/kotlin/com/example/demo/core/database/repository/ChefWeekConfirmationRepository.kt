package com.example.demo.core.database.repository

import com.example.demo.core.database.entity.ChefWeekConfirmationEntity
import com.example.demo.core.database.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface ChefWeekConfirmationRepository : JpaRepository<ChefWeekConfirmationEntity, Long> {
    fun findByChefAndWeekStart(chef: UserEntity, weekStart: LocalDate): ChefWeekConfirmationEntity?
}
