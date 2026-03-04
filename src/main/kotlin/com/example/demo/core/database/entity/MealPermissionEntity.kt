package com.example.demo.core.database.entity

import com.example.demo.core.database.NoMealReasonType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate

@Entity
@Table(
    name = "meal_permission",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_meal_permission_student_date", columnNames = ["student_id", "date"])
    ],
    indexes = [
        Index(name = "idx_meal_permission_date_student", columnList = "date,student_id")
    ]
)
class MealPermissionEntity (

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int? = null,

    @Column(name="date")
    val date: LocalDate,

    @ManyToOne
    val student: UserEntity,

    @ManyToOne
    val assignedBy: UserEntity,

    @Column(nullable = false)
    var reason: String,

    @Column(name="breakfast", nullable = false)
    var isBreakfastAllowed: Boolean = false,

    @Column(name="lunch", nullable = false)
    var isLunchAllowed: Boolean = false,

    @Enumerated(EnumType.STRING)
    @Column(name = "no_meal_reason_type")
    var noMealReasonType: NoMealReasonType? = null,

    @Column(name = "no_meal_reason_text", length = 512)
    var noMealReasonText: String? = null,

    @Column(name = "absence_from")
    var absenceFrom: LocalDate? = null,

    @Column(name = "absence_to")
    var absenceTo: LocalDate? = null,

    @Column(columnDefinition = "TEXT")
    var comment: String? = null,

)
