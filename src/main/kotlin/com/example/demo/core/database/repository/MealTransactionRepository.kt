package com.example.demo.core.database.repository

import com.example.demo.core.database.MealType
import com.example.demo.core.database.entity.GroupEntity
import com.example.demo.core.database.entity.MealTransactionEntity
import com.example.demo.core.database.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.time.LocalDateTime

interface MealTransactionRepository : JpaRepository<MealTransactionEntity, Int> {

    fun existsByStudentAndMealTypeAndTimeStampBetween(
        student: UserEntity,
        mealType: MealType,
        start: LocalDateTime,
        end: LocalDateTime
    ): Boolean

    fun existsByTransactionHash(hash: String): Boolean

    fun findAllByStudentAndTimeStampBetween(
        student: UserEntity,
        start: LocalDateTime,
        end: LocalDateTime
    ): List<MealTransactionEntity>

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

    // ← этот метод больше не будем использовать в отчетах, но можно оставить
    @Query("""
        SELECT t FROM MealTransactionEntity t 
        WHERE DATE(t.timeStamp) = :date 
        ORDER BY t.timeStamp DESC
    """)
    fun findAllByDate(@Param("date") date: LocalDate): List<MealTransactionEntity>

    @Query("""
        SELECT t FROM MealTransactionEntity t 
        WHERE t.student = :student 
        AND DATE(t.timeStamp) = :date
    """)
    fun findAllByStudentAndDate(
        @Param("student") student: UserEntity,
        @Param("date") date: LocalDate
    ): List<MealTransactionEntity>

    // НОВЫЙ метод — для отчетов
    fun findAllByTimeStampBetween(
        start: LocalDateTime,
        end: LocalDateTime
    ): List<MealTransactionEntity>

    @Query(
        """
        SELECT t FROM MealTransactionEntity t
        WHERE t.student.group IN :groups
        AND t.timeStamp BETWEEN :start AND :end
    """
    )
    fun findAllByStudentGroupInAndTimeStampBetween(
        @Param("groups") groups: Collection<GroupEntity>,
        @Param("start") start: LocalDateTime,
        @Param("end") end: LocalDateTime
    ): List<MealTransactionEntity>
}
