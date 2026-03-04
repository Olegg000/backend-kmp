package com.example.demo.features.menu

import com.example.demo.config.TestProfileResolver
import com.example.demo.core.database.entity.MenuEntity
import com.example.demo.core.database.repository.MenuRepository
import com.example.demo.core.exception.BusinessException
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
@ActiveProfiles(resolver = TestProfileResolver::class)
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
            location = "Корпус А",
            description = "Гороховый"
        )

        val saved = menuService.addMenuItem(dto)

        assertNotNull(saved.id)
        assertEquals("Суп", saved.name)
        assertEquals("Корпус А", saved.location)

        val list = menuService.getMenuForDate(date)
        assertEquals(1, list.size)
        assertEquals("Суп", list[0].name)
        assertEquals("Корпус А", list[0].location)
    }

    @Test
    @DisplayName("addMenuItemsBatch сохраняет несколько блюд")
    fun `addMenuItemsBatch works`() {
        val date = LocalDate.now()
        val items = listOf(
            CreateMenuItemRequest(date, "Суп", "Корпус А", "1"),
            CreateMenuItemRequest(date, "Каша", "Корпус Б", "2")
        )

        val saved = menuService.addMenuItemsBatch(items)
        assertEquals(2, saved.size)

        val fromRepo = menuRepository.findAllByDate(date)
        assertEquals(2, fromRepo.size)
    }

    @Test
    @DisplayName("addMenuItem отклоняет пустую location")
    fun `addMenuItem rejects blank location`() {
        val date = LocalDate.now()
        val ex = assertThrows(BusinessException::class.java) {
            menuService.addMenuItem(
                CreateMenuItemRequest(
                    date = date,
                    name = "Суп",
                    location = " ",
                    description = "Гороховый"
                )
            )
        }

        assertEquals("MENU_LOCATION_REQUIRED", ex.code)
    }

    @Test
    @DisplayName("getMenuForDate фильтрует по location")
    fun `getMenuForDate filters by location`() {
        val date = LocalDate.now()
        menuRepository.save(
            MenuEntity(
                date = date,
                name = "Компот",
                location = "Корпус А",
                description = "Яблочный",
            )
        )
        menuRepository.save(
            MenuEntity(
                date = date,
                name = "Каша",
                location = "Корпус Б",
                description = "Гречневая",
            )
        )

        val filtered = menuService.getMenuForDate(date, "корпус а")
        assertEquals(1, filtered.size)
        assertEquals("Корпус А", filtered.first().location)
    }

    @Test
    @DisplayName("deleteItem удаляет блюдо")
    fun `deleteItem removes menu item`() {
        val date = LocalDate.now()
        val entity = menuRepository.save(
            MenuEntity(
                date = date,
                name = "Компот",
                location = "Корпус А",
                description = "Фруктовый"
            )
        )

        assertEquals(1, menuRepository.findAll().size)

        menuService.deleteItem(entity.id!!)

        assertEquals(0, menuRepository.findAll().size)
    }
}
