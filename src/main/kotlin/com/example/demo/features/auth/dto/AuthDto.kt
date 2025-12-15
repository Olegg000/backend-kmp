package com.example.demo.features.auth.dto

import com.example.demo.core.database.Role
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
    val groupId: Int?
)

data class RegUser (
    val login: String,
    val password: String,
    val roles: Role,
    val name: String,
    val surname: String,
    val fatherName: String,
)