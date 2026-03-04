package com.example.demo.features.menu.controller

import com.example.demo.features.menu.dto.CreateMenuItemRequest
import com.example.demo.features.menu.dto.MenuItemDto
import com.example.demo.features.menu.service.MenuService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/v1/menu")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Menu", description = "Управление меню столовой")
class MenuController(
    private val menuService: MenuService,
    private val businessClock: Clock,
) {

    @GetMapping
    @Operation(summary = "Получить меню на дату (по дефолту - сегодня)")
    fun getMenu(
        @RequestParam(required = false) date: LocalDate?,
        @RequestParam(required = false) location: String?,
    ): List<MenuItemDto> {
        // Если дата не передана, берем сегодня
        val targetDate = date ?: LocalDate.now(businessClock)
        return menuService.getMenuForDate(targetDate, location)
    }

    @GetMapping("/locations")
    @Operation(summary = "Получить доступные локации меню на дату (по дефолту - сегодня)")
    fun getMenuLocations(@RequestParam(required = false) date: LocalDate?): List<String> {
        val targetDate = date ?: LocalDate.now(businessClock)
        return menuService.getLocationsForDate(targetDate)
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CHEF', 'ADMIN')") // Только повар!
    @Operation(summary = "Добавить блюдо в меню")
    fun addMenuItem(@RequestBody req: CreateMenuItemRequest): MenuItemDto {
        return menuService.addMenuItem(req)
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('CHEF', 'ADMIN')")
    @Operation(summary = "Удалить блюдо")
    fun deleteMenuItem(@PathVariable id: UUID) {
        menuService.deleteItem(id)
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('CHEF', 'ADMIN')")
    @Operation(summary = "Массовая загрузка меню (списком)")
    fun addMenuBatch(@RequestBody items: List<CreateMenuItemRequest>): List<MenuItemDto> {
        return menuService.addMenuItemsBatch(items)
    }
}
