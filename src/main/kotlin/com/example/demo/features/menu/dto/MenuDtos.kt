package com.example.demo.features.menu.dto

import java.time.LocalDate
import java.util.UUID

data class MenuItemDto(
    val id: UUID,
    val date: LocalDate,
    val name: String,
    val description: String?,
    val photoUrl: String?
)

data class CreateMenuItemRequest(
    val date: LocalDate,
    val name: String,
    val description: String?
)