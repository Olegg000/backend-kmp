package com.example.demo.core.database.repository

import com.example.demo.core.database.entity.MenuEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.util.UUID

interface MenuRepository: JpaRepository<MenuEntity, UUID> {
    fun findAllByDate(date: LocalDate): List<MenuEntity>
    fun findAllByDateAndLocationIgnoreCase(date: LocalDate, location: String): List<MenuEntity>

    @Query(
        """
        select distinct m.location
        from MenuEntity m
        where m.date = :date
        order by m.location
        """
    )
    fun findDistinctLocationsByDate(@Param("date") date: LocalDate): List<String>
}
