package com.example.demo.features.groups.dto

import java.util.UUID

// Запрос на создание группы
data class CreateGroupRequest(
    val name: String
)

data class CuratorSummary(
    val id: UUID,
    val name: String,
    val surname: String,
    val fatherName: String
)

// Ответ: Данные о группе (для списков)
data class GroupResponse(
    val id: Int,
    val name: String,
    val curators: List<CuratorSummary>,
    val studentCount: Int
)

// Запрос на добавление студента (по ID или Логину)
data class AddStudentRequest(
    val studentId: UUID
)
