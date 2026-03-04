package com.example.demo.core.database.repository

import com.example.demo.core.database.AccountStatus
import com.example.demo.core.database.Role
import com.example.demo.core.database.entity.GroupEntity
import com.example.demo.core.database.entity.UserEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface UserRepository: JpaRepository<UserEntity, UUID> {
    fun findByLogin(login: String): UserEntity?

    fun countByGroup_Id(groupId: Int): Int

    // Найти всех студентов группы
    fun findAllByGroup(group: GroupEntity): List<UserEntity>

    // Найти всех пользователей с определенной ролью
    @Query("SELECT u FROM UserEntity u JOIN u.roles r WHERE r = :role")
    fun findAllByRole(@Param("role") role: Role): List<UserEntity>

    @Query("SELECT u FROM UserEntity u JOIN u.roles r WHERE r = :role AND u.accountStatus = :status")
    fun findAllByRoleAndAccountStatus(
        @Param("role") role: Role,
        @Param("status") status: AccountStatus
    ): List<UserEntity>

    @Query(
        """
        SELECT DISTINCT u
        FROM UserEntity u
        JOIN u.roles r
        WHERE r = com.example.demo.core.database.Role.STUDENT
          AND u.publicKey IS NOT NULL
    """
    )
    fun findAllStudentsWithPublicKey(): List<UserEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserEntity u WHERE u.id = :id")
    fun findByIdForUpdate(@Param("id") id: UUID): UserEntity?

    // Проверка существования пользователя по логину
    fun existsByLogin(login: String): Boolean
}
