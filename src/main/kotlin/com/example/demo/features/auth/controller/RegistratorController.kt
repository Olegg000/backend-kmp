package com.example.demo.features.auth.controller

import com.example.demo.core.database.Role
import com.example.demo.features.auth.dto.AdminUserDto
import com.example.demo.features.auth.dto.CreateUserRequest
import com.example.demo.features.auth.dto.RegistrationDto
import com.example.demo.features.auth.dto.UpdateUserRolesRequest
import com.example.demo.features.auth.dto.UserCredentialsResponse
import com.example.demo.features.auth.service.UserServiceQ
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.nio.charset.StandardCharsets
import java.security.Principal
import java.util.UUID

@RestController
@RequestMapping("/api/v1/registrator")
@SecurityRequirement(name = "bearerAuth")
class RegistratorController(
    private val userService: UserServiceQ
) {

    @PostMapping("/users")
    fun createUser(@RequestBody @Valid dto: RegistrationDto) {
        userService.registerUser(dto)
    }

    @PostMapping("/users/create")
    @PreAuthorize("hasAnyRole('REGISTRATOR', 'ADMIN')")
    @Operation(summary = "Создать пользователя и получить пароль")
    fun createAuto(@RequestBody @Valid req: CreateUserRequest): UserCredentialsResponse {
        return userService.createUserAuto(req)
    }

    @PostMapping("/users/{userId}/reset-password")
    @PreAuthorize("hasAnyRole('REGISTRATOR', 'ADMIN')")
    fun resetPass(
        @PathVariable userId: UUID,
        principal: Principal
    ): UserCredentialsResponse {
        return userService.resetPassword(userId, principal.name)
    }


    @PostMapping("/import/students", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @PreAuthorize("hasAnyRole('REGISTRATOR', 'ADMIN')")
    fun uploadStudents(@RequestParam("file") file: MultipartFile) {
        val content = String(file.bytes, StandardCharsets.UTF_8) // Или windows-1251 для русской винды
        userService.importStudentsFromCsv(content)
    }

    @PatchMapping("/users/{userId}/roles")
    @PreAuthorize("hasAnyRole('ADMIN', 'REGISTRATOR')")
    @Operation(summary = "Обновить роли пользователя")
    fun updateUserRoles(
        principal: Principal,
        @PathVariable userId: UUID,
        @RequestBody @Valid request: UpdateUserRolesRequest
    ): AdminUserDto {
        return userService.updateUserRoles(userId, request.roles, request.groupId, request.studentCategory)
    }

    @DeleteMapping("/users/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'REGISTRATOR')")
    @Operation(summary = "Удалить пользователя")
    fun deleteUser(
        principal: Principal,
        @PathVariable userId: UUID
    ) {
        userService.deleteUser(userId, principal.name)
    }

    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('ADMIN', 'REGISTRATOR')")
    @Operation(summary = "Получить список пользователей")
    fun listUsers(
        @RequestParam(required = false) role: Role?,
        @RequestParam(required = false) groupId: Int?,
        @RequestParam(required = false) search: String?
    ): List<AdminUserDto> {
        return userService.listUsers(role, groupId, search)
    }

}
