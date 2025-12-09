package com.example.demo.core.database.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "menu_items")
class MenuEntity(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(nullable = false)
    val date: LocalDate,

    @Column(nullable = false)
    val name: String, // "Суп гороховый"

    @Column(columnDefinition = "TEXT")
    val description: String? = null, // "С копченостями, 250гр"

    val photoUrl: String? = null // Ссылка на фото (если будем грузить)
)