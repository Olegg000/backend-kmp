package com.example.demo.core.database.repository

import com.example.demo.core.database.entity.GroupEntity
import org.springframework.data.jpa.repository.JpaRepository

interface GroupRepository: JpaRepository<GroupEntity, Int>{
    fun findByGroupName(groupName: String): GroupEntity?
    fun existsByGroupName(groupName: String): Boolean
}