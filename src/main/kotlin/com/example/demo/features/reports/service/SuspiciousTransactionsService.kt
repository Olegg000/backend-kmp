package com.example.demo.features.reports.service

import com.example.demo.core.database.MealType
import com.example.demo.core.database.entity.SuspiciousTransactionEntity
import com.example.demo.core.database.repository.SuspiciousTransactionRepository
import com.example.demo.features.reports.dto.SuspiciousTransactionDto
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class SuspiciousTransactionsService(
    private val suspiciousRepo: SuspiciousTransactionRepository
) {

    fun getSuspicious(
        startDate: LocalDate,
        endDate: LocalDate,
        mealType: MealType? = null,
        resolved: Boolean? = null
    ): List<SuspiciousTransactionDto> {
        val all = suspiciousRepo.findAllByDateBetween(startDate, endDate)

        return all
            .filter { mealType == null || it.mealType == mealType }
            .filter { resolved == null || it.resolved == resolved }
            .map { it.toDto() }
    }

    private fun SuspiciousTransactionEntity.toDto() =
        SuspiciousTransactionDto(
            id = this.id!!,
            date = this.date,
            mealType = this.mealType,
            studentId = this.student.id!!,
            studentName = "${student.surname} ${student.name}",
            groupName = student.group?.groupName,
            chefId = this.chef?.id,
            chefName = this.chef?.let { "${it.surname} ${it.name}" },
            reason = this.reason,
            attemptTimestamp = this.attemptTimestamp,
            resolved = this.resolved
        )

    fun exportToCsv(
        startDate: LocalDate,
        endDate: LocalDate,
        mealType: MealType? = null,
        resolved: Boolean? = null
    ): String {
        val list = getSuspicious(startDate, endDate, mealType, resolved)

        val header = "Дата,Тип питания,Студент,Группа,Повар,Причина,Время попытки,Статус\n"

        val rows = list.map { dto ->
            val status = if (dto.resolved) "Решено" else "Новое"
            val chef = dto.chefName ?: "-"
            val group = dto.groupName ?: "-"
            val mealType = when (dto.mealType) {
                MealType.BREAKFAST -> "Завтрак"
                MealType.LUNCH -> "Обед"
            }
            val reason = when (dto.reason.uppercase()) {
                "ALREADY_ATE" -> "Повторная попытка питания"
                "INVALID_SIGNATURE" -> "Неверная подпись QR"
                "EXPIRED_QR" -> "Просроченный QR-код"
                "NOT_ALLOWED" -> "Питание не разрешено"
                else -> dto.reason
            }

            "${dto.date}," +
                    "$mealType," +
                    "\"${dto.studentName}\"," +
                    "\"$group\"," +
                    "\"$chef\"," +
                    "\"$reason\"," +
                    "${dto.attemptTimestamp}," +
                    status
        }

        return header + rows.joinToString("\n")
    }
}
