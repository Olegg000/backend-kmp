package com.example.demo.features.curator.dto

import com.example.demo.core.database.StudentCategory
import java.util.UUID

data class CuratorStudentCategoryUpdateRequest(
    val studentCategory: StudentCategory
)

data class CuratorStudentRow(
    val userId: UUID,
    val fullName: String,
    val groupId: Int,
    val groupName: String,
    val studentCategory: StudentCategory?
)
