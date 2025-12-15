package com.example.demo.features.reports.service

import com.example.demo.features.reports.dto.SuspiciousTransactionDto
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
            val status = if (dto.resolved) "RESOLVED" else "NEW"
            table.addCell(Paragraph(dto.date.toString(), fontNormal))
            table.addCell(Paragraph(dto.mealType.toString(), fontNormal))
            table.addCell(Paragraph(dto.studentName, fontNormal))
            table.addCell(Paragraph(dto.groupName ?: "-", fontNormal))
            table.addCell(Paragraph(dto.chefName ?: "-", fontNormal))
            table.addCell(Paragraph(dto.reason, fontNormal))
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