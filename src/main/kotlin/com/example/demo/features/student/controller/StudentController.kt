package com.example.demo.features.student.controller

import com.example.demo.features.student.dto.StudentSelfRosterDto
import com.example.demo.features.student.dto.StudentTodayMealsDto
import com.example.demo.features.student.service.StudentService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.security.Principal
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/student")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Student", description = "Эндпоинты для студентов (личный табель и питание)")
class StudentController(
    private val studentService: StudentService
) {

    @GetMapping("/roster")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Мой табель на неделю")
    fun getMyRoster(
        principal: Principal,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        startDate: LocalDate?
    ): StudentSelfRosterDto {
        return studentService.getSelfRoster(principal.name, startDate)
    }

    @GetMapping("/meals/today")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Мои права питания на сегодня")
    fun getTodayMeals(
        principal: Principal
    ): StudentTodayMealsDto {
        return studentService.getTodayMeals(principal.name)
    }
}