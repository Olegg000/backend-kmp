package com.example.demo.features.menu

import com.example.demo.core.database.entity.MenuEntity
import com.example.demo.core.database.repository.MenuRepository
import com.example.demo.features.menu.dto.CreateMenuItemRequest
import com.example.demo.features.menu.service.MenuService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate

@DataJpaTest
@Import(MenuService::class)
@ActiveProfiles("test")
@DisplayName("MenuService - управление меню")
class MenuServiceTest(

    @Autowired private val menuService: MenuService,
    @Autowired private val menuRepository: MenuRepository
) {

    @Test
    @DisplayName("addMenuItem сохраняет блюдо и getMenuForDate его возвращает")
    fun `addMenuItem and getMenuForDate`() {
        val date = LocalDate.now()

        val dto = CreateMenuItemRequest(
            date = date,
            name = "Суп",
            description = "Гороховый"
        )

        val saved = menuService.addMenuItem(dto)

        assertNotNull(saved.id)
        assertEquals("Суп", saved.name)

        val list = menuService.getMenuForDate(date)
        assertEquals(1, list.size)
        assertEquals("Суп", list[0].name)
    }

    @Test
    @DisplayName("addMenuItemsBatch сохраняет несколько блюд")
    fun `addMenuItemsBatch works`() {
        val date = LocalDate.now()
        val items = listOf(
            CreateMenuItemRequest(date, "Суп", "1"),
            CreateMenuItemRequest(date, "Каша", "2")
        )

        val saved = menuService.addMenuItemsBatch(items)
        assertEquals(2, saved.size)

        val fromRepo = menuRepository.findAllByDate(date)
        assertEquals(2, fromRepo.size)
    }

    @Test
    @DisplayName("deleteItem удаляет блюдо")
    fun `deleteItem removes menu item`() {
        val date = LocalDate.now()
        val entity = menuRepository.save(
            MenuEntity(
                date = date,
                name = "Компот",
                description = "Фруктовый"
            )
        )

        assertEquals(1, menuRepository.findAll().size)

        menuService.deleteItem(entity.id!!)

        assertEquals(0, menuRepository.findAll().size)
    }
}