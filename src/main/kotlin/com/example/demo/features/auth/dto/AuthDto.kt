package com.example.demo.features.auth.dto

import com.example.demo.core.database.Role
import com.example.demo.core.database.StudentCategory
import java.util.UUID

data class Auth (
    val login: String,
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
    val studentCategory: StudentCategory?
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
    val privateKey: String
)

data class RegUser (
    val login: String,
    val password: String,
    val roles: Role,
    val name: String,
    val surname: String,
    val fatherName: String,
)

data class UpdateUserRolesRequest(
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
    val studentCategory: StudentCategory?
)

data class AuthKeysDto(
    val publicKey: String,
    val privateKey: String
)
