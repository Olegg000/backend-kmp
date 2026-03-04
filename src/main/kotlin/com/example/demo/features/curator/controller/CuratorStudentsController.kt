package com.example.demo.features.curator.controller

import com.example.demo.features.auth.dto.AdminUserDto
import com.example.demo.features.curator.dto.CuratorStudentAbsenceRequest
import com.example.demo.features.curator.dto.CuratorStudentCategoryUpdateRequest
import com.example.demo.features.curator.dto.CuratorStudentRow
import com.example.demo.features.curator.service.CuratorStudentService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.security.Principal
import java.util.UUID

@RestController
@RequestMapping("/api/v1/curator/students")
@SecurityRequirement(name = "bearerAuth")
class CuratorStudentsController(
    private val curatorStudentService: CuratorStudentService
) {

    @PatchMapping("/{studentId}/category")
    @PreAuthorize("hasRole('CURATOR')")
    @Operation(summary = "Изменить категорию студента в группе куратора")
    fun updateCategory(
        principal: Principal,
        @PathVariable studentId: UUID,
        @RequestBody request: CuratorStudentCategoryUpdateRequest
    ): AdminUserDto {
        return curatorStudentService.updateStudentCategory(principal.name, studentId, request)
    }

    @GetMapping
    @PreAuthorize("hasRole('CURATOR')")
    @Operation(summary = "Список студентов групп куратора")
    fun listStudents(
        principal: Principal,
        @RequestParam(required = false) groupId: Int?
    ): List<CuratorStudentRow> {
        return curatorStudentService.listMyStudents(principal.name, groupId)
    }

    @PostMapping("/{studentId}/absence")
    @PreAuthorize("hasRole('CURATOR')")
    @Operation(summary = "Назначить период отсутствия питания студенту")
    fun assignAbsence(
        principal: Principal,
        @PathVariable studentId: UUID,
        @RequestBody request: CuratorStudentAbsenceRequest
    ) {
        curatorStudentService.applyAbsence(principal.name, studentId, request)
    }
}
