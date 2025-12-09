package com.example.demo.core.database.repository

import com.example.demo.core.database.entity.MenuEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.util.UUID

interface MenuRepository: JpaRepository<MenuEntity, UUID> {
    fun findAllByDate(date: LocalDate): List<MenuEntity>
}