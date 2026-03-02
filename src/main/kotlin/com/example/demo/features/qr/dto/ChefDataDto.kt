package com.example.demo.features.qr.dto

import java.util.UUID

/**
 * Публичный ключ студента для оффлайн-верификации (скачивается поваром)
 */
data class StudentKeyDto(
    val userId: UUID,
    val publicKey: String,
    val name: String,
    val surname: String,
    val fatherName: String,
    val groupName: String?
)

/**
 * Разрешение на питание студента на конкретную дату (скачивается поваром)
 */
data class StudentPermissionDto(
    val studentId: UUID,
    val name: String,
    val surname: String,
    val breakfast: Boolean,
    val lunch: Boolean
)
