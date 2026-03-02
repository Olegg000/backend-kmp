package com.example.demo.features.auth.controller

import com.example.demo.features.auth.dto.AdminUserDto
import com.example.demo.features.auth.dto.UpdateUserCategoryRequest
import com.example.demo.features.auth.service.UserServiceQ
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/users")
@SecurityRequirement(name = "bearerAuth")
class UserCategoryController(
    private val userService: UserServiceQ
) {

    @PatchMapping("/{userId}/category")
    @PreAuthorize("hasAnyRole('ADMIN', 'REGISTRATOR')")
    @Operation(summary = "Изменить категорию студента")
    fun updateUserCategory(
        @PathVariable userId: UUID,
        @RequestBody request: UpdateUserCategoryRequest
    ): AdminUserDto {
        return userService.updateStudentCategory(userId, request.studentCategory)
    }
}
