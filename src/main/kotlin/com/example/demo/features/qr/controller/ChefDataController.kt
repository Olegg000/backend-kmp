package com.example.demo.features.qr.controller

import com.example.demo.features.qr.dto.StudentKeyDto
import com.example.demo.features.qr.dto.StudentPermissionDto
import com.example.demo.features.qr.service.ChefDataService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/chef")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Chef Data", description = "Данные для оффлайн-работы повара")
class ChefDataController(
    private val chefDataService: ChefDataService
) {

    @GetMapping("/keys")
    @PreAuthorize("hasAnyRole('CHEF', 'ADMIN')")
    @Operation(summary = "Скачать публичные ключи студентов для оффлайн-верификации")
    fun getStudentKeys(): List<StudentKeyDto> {
        return chefDataService.getAllStudentKeys()
    }

    @GetMapping("/permissions/today")
    @PreAuthorize("hasAnyRole('CHEF', 'ADMIN')")
    @Operation(summary = "Скачать разрешения на питание на сегодня")
    fun getTodayPermissions(): List<StudentPermissionDto> {
        return chefDataService.getTodayPermissions()
    }
}
