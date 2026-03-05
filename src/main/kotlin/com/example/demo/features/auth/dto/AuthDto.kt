package com.example.demo.features.auth.dto

import com.example.demo.core.database.AccountStatus
import com.example.demo.core.database.Role
import com.example.demo.core.database.StudentCategory
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.util.UUID

data class Auth (
    @field:NotBlank
    @field:Size(min = 3, max = 64)
    val login: String,

    @field:NotBlank
    @field:Size(min = 6, max = 128)
    val password: String
)

data class AuthReturns(
    val token: String,
    val roles: List<String>,
    val privateKey: String,
    val publicKey: String,

    val userId: UUID,
    val login: String,
    val name: String,
    val surname: String,
    val fatherName: String,
    val groupId: Int?,
    val studentCategory: StudentCategory?,
    val testMode: Boolean,
)

data class AuthMeResponse(
    val userId: UUID,
    val roles: List<String>,
    val name: String,
    val surname: String,
    val fatherName: String,
    val groupId: Int?,
    val studentCategory: StudentCategory?,
    val publicKey: String,
    val privateKey: String,
    val testMode: Boolean,
)

data class RegUser (
    @field:NotBlank
    @field:Size(min = 3, max = 64)
    val login: String,

    @field:NotBlank
    @field:Size(min = 6, max = 128)
    val password: String,
    val roles: Role,
    val name: String,
    val surname: String,
    val fatherName: String,
)

data class UpdateUserRolesRequest(
    @field:NotEmpty
    val roles: Set<Role>,
    val groupId: Int? = null,
    val studentCategory: StudentCategory? = null,
)

data class AdminUserDto(
    val userId: UUID,
    val login: String,
    val roles: Set<Role>,
    val name: String,
    val surname: String,
    val fatherName: String,
    val groupId: Int?,
    val studentCategory: StudentCategory?,
    val accountStatus: AccountStatus,
)

data class UpdateLifecycleRequest(
    val status: AccountStatus,
    val expelNote: String? = null,
)

data class AuthKeysDto(
    val publicKey: String,
    val privateKey: String
)
