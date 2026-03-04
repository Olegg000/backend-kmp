package com.example.demo.core.database.repository

import com.example.demo.core.database.entity.GroupEntity
import com.example.demo.core.database.entity.MealPermissionEntity
import com.example.demo.core.database.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface MealPermissionRepository : JpaRepository<MealPermissionEntity, Int> {

    fun findByStudentAndDate(student: UserEntity, date: LocalDate): MealPermissionEntity?

    fun findAllByDate(date: LocalDate): List<MealPermissionEntity>

    fun findAllByDateBetween(startDate: LocalDate, endDate: LocalDate): List<MealPermissionEntity>

    fun findAllByStudentAndDateIn(student: UserEntity, dates: List<LocalDate>): List<MealPermissionEntity>

    fun findAllByStudentInAndDateBetween(
        students: Collection<UserEntity>,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<MealPermissionEntity>

    // Найти все разрешения для студентов группы за период
    @Query("""
        SELECT p FROM MealPermissionEntity p 
        WHERE p.student.group = :group 
        AND p.date BETWEEN :startDate AND :endDate
    """)
    fun findAllByGroupAndDateRange(
        @Param("group") group: GroupEntity,
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate
    ): List<MealPermissionEntity>

    @Query(
        """
        SELECT p FROM MealPermissionEntity p
        WHERE p.student.group IN :groups
        AND p.date BETWEEN :startDate AND :endDate
    """
    )
    fun findAllByGroupsAndDateRange(
        @Param("groups") groups: Collection<GroupEntity>,
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate
    ): List<MealPermissionEntity>

    @Query(
        """
        SELECT COUNT(p)
        FROM MealPermissionEntity p
        WHERE p.student.group IN :groups
          AND p.date BETWEEN :startDate AND :endDate
    """
    )
    fun countByGroupsAndDateRange(
        @Param("groups") groups: Collection<GroupEntity>,
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate
    ): Long

    @Query(
        """
        SELECT p
        FROM MealPermissionEntity p
        WHERE p.student.group IN :groups
          AND p.date BETWEEN :startDate AND :endDate
    """
    )
    fun findAllByGroupsAndDateRangeWithStudents(
        @Param("groups") groups: Collection<GroupEntity>,
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate
    ): List<MealPermissionEntity>

    // Проверка: заполнен ли табель для студента на неделю
    @Query("""
        SELECT COUNT(p) > 0 FROM MealPermissionEntity p 
        WHERE p.student = :student 
        AND p.date BETWEEN :startDate AND :endDate
    """)
    fun existsForStudentInRange(
        @Param("student") student: UserEntity,
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate
    ): Boolean

    // Удалить все разрешения студента на определенную дату
    fun deleteByStudentAndDate(student: UserEntity, date: LocalDate)

    // Найти всех студентов, у которых НЕТ разрешений на определенную дату
    @Query("""
        SELECT u FROM UserEntity u 
        WHERE u.group = :group 
        AND NOT EXISTS (
            SELECT 1 FROM MealPermissionEntity p 
            WHERE p.student = u AND p.date = :date
        )
    """)
    fun findStudentsWithoutPermission(
        @Param("group") group: GroupEntity,
        @Param("date") date: LocalDate
    ): List<UserEntity>
}
