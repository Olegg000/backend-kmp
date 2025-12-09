package com.example.demo.features.auth.controller

import com.example.demo.features.auth.dto.CreateUserRequest
import com.example.demo.features.auth.dto.RegistrationDto
import com.example.demo.features.auth.dto.UserCredentialsResponse
import com.example.demo.features.auth.service.UserServiceQ
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.nio.charset.StandardCharsets
import java.util.UUID

@RestController
@RequestMapping("/api/v1/registrator")
@SecurityRequirement(name = "bearerAuth")
class RegistratorController(
    private val userService: UserServiceQ
) {

    @PostMapping("/users")
    fun createUser(@RequestBody dto: RegistrationDto) {
        userService.registerUser(dto)
    }

    @PostMapping("/users/create")
    @PreAuthorize("hasAnyRole('REGISTRATOR', 'ADMIN')")
    @Operation(summary = "Создать пользователя и получить пароль")
    fun createAuto(@RequestBody req: CreateUserRequest): UserCredentialsResponse {
        return userService.createUserAuto(req)
    }

    @PostMapping("/users/{userId}/reset-password")
    @PreAuthorize("hasAnyRole('REGISTRATOR', 'ADMIN')")
    @Operation(summary = "Сгенерировать новый пароль пользователю")
    fun resetPass(@PathVariable userId: UUID): UserCredentialsResponse {
        return userService.resetPassword(userId)
    }


    @PostMapping("/import/students", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @PreAuthorize("hasAnyRole('REGISTRATOR', 'ADMIN')")
    fun uploadStudents(@RequestParam("file") file: MultipartFile) {
        val content = String(file.bytes, StandardCharsets.UTF_8) // Или windows-1251 для русской винды
        userService.importStudentsFromCsv(content)
    }

}