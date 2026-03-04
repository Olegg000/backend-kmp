package com.example.demo.features.auth.dto

import com.example.demo.core.database.Role
import com.example.demo.core.database.StudentCategory
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.util.UUID

data class RegistrationDto(
    @field:NotBlank
    @field:Size(min = 3, max = 64)
    val login: String,

    @field:NotBlank
    @field:Size(min = 8, max = 128)
    val password: String, // В продакшене тут может быть дефолтный пароль

    @field:NotEmpty
    val roles: Set<Role>,

    @field:NotBlank
    @field:Size(min = 1, max = 128)
    val name: String,

    @field:NotBlank
    @field:Size(min = 1, max = 128)
    val surname: String,

    @field:NotBlank
    @field:Size(min = 1, max = 128)
    val fatherName: String,
    val groupId: Int? = null, // Опционально, только для студентов
    val studentCategory: StudentCategory? = null
)

data class CreateUserRequest(
    @field:NotEmpty
    val roles: Set<Role>,

    @field:NotBlank
    @field:Size(min = 1, max = 128)
    val name: String,

    @field:NotBlank
    @field:Size(min = 1, max = 128)
    val surname: String,

    @field:NotBlank
    @field:Size(min = 1, max = 128)
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
    @field:NotNull
    val studentCategory: StudentCategory
)
