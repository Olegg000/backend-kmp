package com.example.demo.features.roster.controller

import com.example.demo.features.roster.dto.StudentRosterRow
import com.example.demo.features.roster.dto.UpdateRosterRequest
import com.example.demo.features.roster.service.RosterService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.security.Principal
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/roster")
@SecurityRequirement(name = "bearerAuth")
class RosterController(
    private val rosterService: RosterService
) {

    @GetMapping
    @PreAuthorize("hasRole('CURATOR')")
    @Operation(summary = "Получить табель своей группы на неделю")
    fun getMyRoster(
        @RequestParam date: LocalDate,
        @RequestParam(required = false) groupId: Int?,
        principal: Principal
    ): List<StudentRosterRow> {
        return rosterService.getRosterForGroup(principal.name, date, groupId)
    }

    @PostMapping
    @PreAuthorize("hasRole('CURATOR')")
    @Operation(summary = "Сохранить изменения по одному студенту")
    fun updateStudentRoster(
        @RequestBody req: UpdateRosterRequest,
        principal: Principal
    ) {
        rosterService.updateRoster(req, principal.name)
    }
}
