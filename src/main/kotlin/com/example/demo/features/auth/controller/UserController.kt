package com.example.demo.features.auth.controller

import com.example.demo.features.auth.dto.Auth
import com.example.demo.features.auth.dto.AuthReturns
import com.example.demo.features.auth.service.UserServiceQ
import com.sun.net.httpserver.Request
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


@RestController
@RequestMapping("/api/v1/auth")
@SecurityRequirement(name = "bearerAuth")
class UserController(
    private val userService: UserServiceQ
) {

    @PostMapping("/login")
    fun login(@RequestBody request: Auth): AuthReturns {
        return userService.auth(request)
    }

    @org.springframework.web.bind.annotation.GetMapping("/my-keys")
    @io.swagger.v3.oas.annotations.Operation(summary = "Получить свои криптографические ключи")
    fun getMyKeys(): com.example.demo.features.auth.dto.AuthKeysDto {
        val login = org.springframework.security.core.context.SecurityContextHolder.getContext().authentication?.name
            ?: throw RuntimeException("Not authenticated")
        return userService.getMyKeys(login)
    }

}