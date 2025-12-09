package com.example.demo.features.groups.dto

import java.util.UUID

// Запрос на создание группы
data class CreateGroupRequest(
    val name: String
)

// Ответ: Данные о группе (для списков)
data class GroupResponse(
    val id: Int,
    val name: String,
    val curatorId: UUID?,
    val curatorName: String?,
    val curatorSurname: String?,
    val curatorFatherName: String?,
    val studentCount: Int
)

// Запрос на добавление студента (по ID или Логину)
data class AddStudentRequest(
    val studentId: UUID
)