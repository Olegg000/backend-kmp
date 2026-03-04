package com.example.demo.features.menu.service

import com.example.demo.core.database.entity.MenuEntity
import com.example.demo.core.database.repository.MenuRepository
import com.example.demo.core.exception.BusinessException
import com.example.demo.features.menu.dto.CreateMenuItemRequest
import com.example.demo.features.menu.dto.MenuItemDto
import jakarta.transaction.Transactional
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.UUID

@Service
class MenuService(
    private val menuRepository: MenuRepository
) {

    // Получить меню на конкретный день
    fun getMenuForDate(date: LocalDate, location: String? = null): List<MenuItemDto> {
        val trimmedLocation = location?.trim()?.ifBlank { null }
        val entities = if (trimmedLocation == null) {
            menuRepository.findAllByDate(date)
        } else {
            menuRepository.findAllByDateAndLocationIgnoreCase(date, trimmedLocation)
        }
        return entities.map {
            MenuItemDto(it.id!!, it.date, it.name, it.location, it.description, it.photoUrl)
        }
    }

    fun getLocationsForDate(date: LocalDate): List<String> {
        return menuRepository.findDistinctLocationsByDate(date)
    }

    @Transactional
    fun addMenuItemsBatch(items: List<CreateMenuItemRequest>): List<MenuItemDto> {
        val entities = items.map { req ->
            val location = req.location?.trim()?.ifBlank {
                throw BusinessException(
                    code = "MENU_LOCATION_REQUIRED",
                    userMessage = "Укажите место питания.",
                    status = HttpStatus.BAD_REQUEST,
                )
            } ?: throw BusinessException(
                code = "MENU_LOCATION_REQUIRED",
                userMessage = "Укажите место питания.",
                status = HttpStatus.BAD_REQUEST,
            )
            MenuEntity(
                date = req.date,
                name = req.name,
                location = location,
                description = req.description
            )
        }
        val savedList = menuRepository.saveAll(entities)
        return savedList.map { MenuItemDto(it.id!!, it.date, it.name, it.location, it.description, it.photoUrl) }
    }

    // Добавить блюдо
    fun addMenuItem(req: CreateMenuItemRequest): MenuItemDto {
        val location = req.location?.trim()?.ifBlank {
            throw BusinessException(
                code = "MENU_LOCATION_REQUIRED",
                userMessage = "Укажите место питания.",
                status = HttpStatus.BAD_REQUEST,
            )
        } ?: throw BusinessException(
            code = "MENU_LOCATION_REQUIRED",
            userMessage = "Укажите место питания.",
            status = HttpStatus.BAD_REQUEST,
        )
        val item = MenuEntity(
            date = req.date,
            name = req.name,
            location = location,
            description = req.description
        )
        val saved = menuRepository.save(item)
        return MenuItemDto(saved.id!!, saved.date, saved.name, saved.location, saved.description, saved.photoUrl)
    }

    // Удалить блюдо
    fun deleteItem(id: UUID) {
        menuRepository.deleteById(id)
    }
}
