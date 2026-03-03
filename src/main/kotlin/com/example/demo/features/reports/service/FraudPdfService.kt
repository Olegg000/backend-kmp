package com.example.demo.features.reports.service

import com.example.demo.features.reports.dto.SuspiciousTransactionDto
import com.example.demo.core.database.MealType
import com.lowagie.text.*
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfWriter
import org.springframework.stereotype.Service
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.time.LocalDate

@Service
class FraudPdfService(
    private val suspiciousService: SuspiciousTransactionsService
) {

    fun generatePdf(
        startDate: LocalDate,
        endDate: LocalDate
    ): ByteArray {
        val list: List<SuspiciousTransactionDto> =
            suspiciousService.getSuspicious(startDate, endDate, mealType = null, resolved = null)

        val baos = ByteArrayOutputStream()
        val document = Document(PageSize.A4.rotate())
        PdfWriter.getInstance(document, baos)
        document.open()

        val fontHeader = Font(Font.HELVETICA, 14f, Font.BOLD)
        val fontNormal = Font(Font.HELVETICA, 9f, Font.NORMAL)

        document.add(Paragraph("Отчёт по подозрительным транзакциям $startDate — $endDate", fontHeader))
        document.add(Paragraph(" "))

        val table = PdfPTable(8)
        table.widthPercentage = 100f
        table.setWidths(floatArrayOf(2f, 2f, 3f, 2f, 3f, 3f, 3f, 2f))

        fun headerCell(text: String): PdfPCell =
            PdfPCell(Paragraph(text, fontNormal)).apply { backgroundColor = Color.LIGHT_GRAY }

        table.addCell(headerCell("Дата"))
        table.addCell(headerCell("Тип питания"))
        table.addCell(headerCell("Студент"))
        table.addCell(headerCell("Группа"))
        table.addCell(headerCell("Повар"))
        table.addCell(headerCell("Причина"))
        table.addCell(headerCell("Время попытки"))
        table.addCell(headerCell("Статус"))

        list.forEach { dto ->
            val status = if (dto.resolved) "Решено" else "Новое"
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
            table.addCell(Paragraph(dto.date.toString(), fontNormal))
            table.addCell(Paragraph(mealType, fontNormal))
            table.addCell(Paragraph(dto.studentName, fontNormal))
            table.addCell(Paragraph(dto.groupName ?: "-", fontNormal))
            table.addCell(Paragraph(dto.chefName ?: "-", fontNormal))
            table.addCell(Paragraph(reason, fontNormal))
            table.addCell(Paragraph(dto.attemptTimestamp.toString(), fontNormal))
            table.addCell(Paragraph(status, fontNormal))
        }

        document.add(table)
        document.add(Paragraph(" "))

        document.add(Paragraph("Администратор: ____________________", fontNormal))
        document.add(Paragraph("Ответственный: ____________________", fontNormal))

        document.close()
        return baos.toByteArray()
    }
}
