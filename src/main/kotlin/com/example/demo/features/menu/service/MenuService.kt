package com.example.demo.features.menu.service

import com.example.demo.core.database.entity.MenuEntity
import com.example.demo.core.database.repository.MenuRepository
import com.example.demo.features.menu.dto.CreateMenuItemRequest
import com.example.demo.features.menu.dto.MenuItemDto
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.UUID

@Service
class MenuService(
    private val menuRepository: MenuRepository
) {

    // Получить меню на конкретный день
    fun getMenuForDate(date: LocalDate): List<MenuItemDto> {
        return menuRepository.findAllByDate(date).map {
            MenuItemDto(it.id!!, it.date, it.name, it.description, it.photoUrl)
        }
    }

    @Transactional
    fun addMenuItemsBatch(items: List<CreateMenuItemRequest>): List<MenuItemDto> {
        val entities = items.map { req ->
            MenuEntity(
                date = req.date,
                name = req.name,
                description = req.description
            )
        }
        val savedList = menuRepository.saveAll(entities)
        return savedList.map { MenuItemDto(it.id!!, it.date, it.name, it.description, it.photoUrl) }
    }

    // Добавить блюдо
    fun addMenuItem(req: CreateMenuItemRequest): MenuItemDto {
        val item = MenuEntity(
            date = req.date,
            name = req.name,
            description = req.description
        )
        val saved = menuRepository.save(item)
        return MenuItemDto(saved.id!!, saved.date, saved.name, saved.description, saved.photoUrl)
    }

    // Удалить блюдо
    fun deleteItem(id: UUID) {
        menuRepository.deleteById(id)
    }
}