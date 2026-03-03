package com.example.demo.core.database.repository

import com.example.demo.core.database.entity.GroupEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface GroupRepository: JpaRepository<GroupEntity, Int>{
    fun findByGroupName(groupName: String): GroupEntity?
    fun existsByGroupName(groupName: String): Boolean

    @Query("SELECT g.groupName FROM GroupEntity g")
    fun findAllGroupNames(): List<String>

    @Query("SELECT DISTINCT g FROM GroupEntity g LEFT JOIN FETCH g.curators")
    fun findAllWithCurators(): List<GroupEntity>

    @Query("SELECT DISTINCT g FROM GroupEntity g JOIN g.curators c WHERE c.id = :curatorId")
    fun findAllByCuratorId(@Param("curatorId") curatorId: UUID): List<GroupEntity>

    @Query(
        """
        SELECT CASE WHEN COUNT(g) > 0 THEN true ELSE false END
        FROM GroupEntity g JOIN g.curators c
        WHERE g.id = :groupId AND c.id = :curatorId
        """
    )
    fun existsByIdAndCuratorId(
        @Param("groupId") groupId: Int,
        @Param("curatorId") curatorId: UUID
    ): Boolean
}
