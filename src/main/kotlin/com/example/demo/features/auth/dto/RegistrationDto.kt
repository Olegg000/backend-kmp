package com.example.demo.features.auth.dto

import com.example.demo.core.database.Role
import com.example.demo.core.database.StudentCategory
import java.util.UUID

data class RegistrationDto(
    val login: String,
    val password: String, // В продакшене тут может быть дефолтный пароль
    val roles: Set<Role>,
    val name: String,
    val surname: String,
    val fatherName: String,
    val groupId: Int? = null, // Опционально, только для студентов
    val studentCategory: StudentCategory? = null
)

data class CreateUserRequest(
    val roles: Set<Role>,
    val name: String,
    val surname: String,
    val fatherName: String,
    val groupId: Int? = null,
    val studentCategory: StudentCategory? = null
)

data class UserCredentialsResponse(
    val userId: UUID,
    val login: String,
    val passwordClearText: String, // Пароль в открытом виде (показываем 1 раз!)
    val fullName: String
)

data class UpdateUserCategoryRequest(
    val studentCategory: StudentCategory
)
