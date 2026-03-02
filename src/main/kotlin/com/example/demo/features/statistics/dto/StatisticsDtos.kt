package com.example.demo.features.statistics.dto

import java.util.UUID

data class StudentMealStatus(
    val studentId: UUID,
    val fullName: String,
    val hadBreakfast: Boolean,
    val hadLunch: Boolean
)
