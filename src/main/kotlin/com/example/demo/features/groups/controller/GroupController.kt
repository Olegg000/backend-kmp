package com.example.demo.features.groups.controller

import com.example.demo.features.groups.dto.CreateGroupRequest
import com.example.demo.features.groups.dto.GroupResponse
import com.example.demo.features.groups.service.GroupService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/groups")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Groups", description = "Управление группами и привязками")
class GroupController(
    private val groupService: GroupService
) {

    @GetMapping
    @Operation(summary = "Получить список всех групп")
    fun getAll(): List<GroupResponse> {
        return groupService.getAllGroups()
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'REGISTRATOR')") // Проверка прав
    @Operation(summary = "Создать новую группу")
    fun create(@RequestBody req: CreateGroupRequest): GroupResponse {
        return groupService.createGroup(req)
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'REGISTRATOR')")
    @Operation(summary = "Удалить группу (студенты будут отвязаны)")
    fun delete(@PathVariable id: Int) {
        groupService.deleteGroup(id)
    }

    // --- Управление Куратором ---

    @PutMapping("/{groupId}/curator/{curatorId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'REGISTRATOR')")
    @Operation(summary = "Назначить куратора группе")
    fun setCurator(
        @PathVariable groupId: Int,
        @PathVariable curatorId: UUID
    ): GroupResponse {
        return groupService.setCurator(groupId, curatorId)
    }

    @DeleteMapping("/{groupId}/curator")
    @PreAuthorize("hasAnyRole('ADMIN', 'REGISTRATOR')")
    @Operation(summary = "Снять куратора с группы")
    fun removeCurator(@PathVariable groupId: Int): GroupResponse {
        return groupService.removeCurator(groupId)
    }

    // --- Управление Студентами ---

    @PostMapping("/{groupId}/students/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'REGISTRATOR')")
    @Operation(summary = "Добавить студента в группу")
    fun addStudent(
        @PathVariable groupId: Int,
        @PathVariable studentId: UUID
    ) {
        groupService.addStudentToGroup(groupId, studentId)
    }

    @DeleteMapping("/students/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'REGISTRATOR')")
    @Operation(summary = "Убрать студента из группы (отвязать)")
    fun removeStudent(@PathVariable studentId: UUID) {
        groupService.removeStudentFromGroup(studentId)
    }
}