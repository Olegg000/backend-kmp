package com.example.demo.core.database.repository

import com.example.demo.core.database.Role
import com.example.demo.core.database.entity.GroupEntity
import com.example.demo.core.database.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
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

    // Проверка существования пользователя по логину
    fun existsByLogin(login: String): Boolean

    // Найти куратора группы
    @Query("SELECT g.curator FROM GroupEntity g WHERE g.id = :groupId")
    fun findCuratorByGroupId(@Param("groupId") groupId: Int): UserEntity?
}