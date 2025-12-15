package com.example.demo.core.database.repository

import com.example.demo.core.database.entity.SuspiciousTransactionEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface SuspiciousTransactionRepository : JpaRepository<SuspiciousTransactionEntity, Int> {

    fun findAllByDateBetween(start: LocalDate, end: LocalDate): List<SuspiciousTransactionEntity>
}