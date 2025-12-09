package com.example.demo.features.statistics.controller

import com.example.demo.features.statistics.dto.StudentMealStatus
import com.example.demo.features.statistics.service.StatisticsService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.security.Principal
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/statistics")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Statistics", description = "Статистика для кураторов")
class CuratorStatsController(
    private val statisticsService: StatisticsService
) {

    @GetMapping("/my-group")
    @PreAuthorize("hasAnyRole('CURATOR', 'ADMIN')")
    @Operation(summary = "Статистика по моей группе (кто поел сегодня)")
    fun getMyGroupStats(
        @RequestParam(required = false) date: LocalDate?,
        principal: Principal
    ): List<StudentMealStatus> {
        val targetDate = date ?: LocalDate.now()
        return statisticsService.getGroupMealStatus(principal.name, targetDate)
    }
}