package com.example.demo.core.database.repository

import com.example.demo.core.database.PushPlatform
import com.example.demo.core.database.entity.PushDeviceTokenEntity
import com.example.demo.core.database.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository

interface PushDeviceTokenRepository : JpaRepository<PushDeviceTokenEntity, Long> {
    fun findByPlatformAndToken(platform: PushPlatform, token: String): PushDeviceTokenEntity?
    fun findAllByUserAndActiveTrue(user: UserEntity): List<PushDeviceTokenEntity>
    fun findAllByPlatformAndTokenAndActiveTrue(platform: PushPlatform, token: String): List<PushDeviceTokenEntity>
}
