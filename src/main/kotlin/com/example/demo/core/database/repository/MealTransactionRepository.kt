package com.example.demo.core.database.repository

import com.example.demo.core.database.MealType
import com.example.demo.core.database.entity.MealTransactionEntity
import com.example.demo.core.database.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

interface MealTransactionRepository : JpaRepository<MealTransactionEntity, Int> {

    // Проверка: ел ли студент этот тип еды в этот промежуток времени?
    fun existsByStudentAndMealTypeAndTimeStampBetween(
        student: UserEntity,
        mealType: MealType,
        start: LocalDateTime,
        end: LocalDateTime
    ): Boolean

    // Проверка по хэшу (защита от дублей при повторной отправке одного и того же пакета)
    fun existsByTransactionHash(hash: String): Boolean

    // Найти все транзакции студента за период
    fun findAllByStudentAndTimeStampBetween(
        student: UserEntity,
        start: LocalDateTime,
        end: LocalDateTime
    ): List<MealTransactionEntity>

    // Статистика: сколько студентов поело по типу еды за день
    @Query("""
        SELECT COUNT(DISTINCT t.student.id) 
        FROM MealTransactionEntity t 
        WHERE t.mealType = :mealType 
        AND t.timeStamp BETWEEN :start AND :end
    """)
    fun countUniqueStudentsByMealTypeAndDate(
        @Param("mealType") mealType: MealType,
        @Param("start") start: LocalDateTime,
        @Param("end") end: LocalDateTime
    ): Long

    // Найти все транзакции за день (для отчетов)
    @Query("""
        SELECT t FROM MealTransactionEntity t 
        WHERE DATE(t.timeStamp) = :date 
        ORDER BY t.timeStamp DESC
    """)
    fun findAllByDate(@Param("date") date: LocalDate): List<MealTransactionEntity>

    // Найти транзакции студента за конкретный день
    @Query("""
        SELECT t FROM MealTransactionEntity t 
        WHERE t.student = :student 
        AND DATE(t.timeStamp) = :date
    """)
    fun findAllByStudentAndDate(
        @Param("student") student: UserEntity,
        @Param("date") date: LocalDate
    ): List<MealTransactionEntity>
}