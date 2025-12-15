package com.example.demo.features.reports.service

import com.example.demo.features.reports.dto.DailyReportResponse
import com.lowagie.text.*
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfWriter
import org.springframework.stereotype.Service
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.time.LocalDate

@Service
class ReportsPdfService(
    private val reportsService: ReportsService
) {

    fun generatePdf(
        startDate: LocalDate,
        endDate: LocalDate
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        val document = Document(PageSize.A4.rotate())
        PdfWriter.getInstance(document, baos)
        document.open()

        val fontHeader = Font(Font.HELVETICA, 14f, Font.BOLD)
        val fontNormal = Font(Font.HELVETICA, 10f, Font.NORMAL)

        document.add(Paragraph("Отчёт по питанию за период $startDate — $endDate", fontHeader))
        document.add(Paragraph(" "))

        val table = PdfPTable(8)
        table.widthPercentage = 100f
        table.setWidths(floatArrayOf(2f, 2f, 2f, 2f, 2f, 2f, 2f, 2f))

        fun headerCell(text: String): PdfPCell =
            PdfPCell(Paragraph(text, fontNormal)).apply { backgroundColor = Color.LIGHT_GRAY }

        table.addCell(headerCell("Дата"))
        table.addCell(headerCell("Завтрак"))
        table.addCell(headerCell("Обед"))
        table.addCell(headerCell("Ужин"))
        table.addCell(headerCell("Полдник"))
        table.addCell(headerCell("Спец.питание"))
        table.addCell(headerCell("Всего"))
        table.addCell(headerCell("Оффлайн"))

        var current = startDate
        while (!current.isAfter(endDate)) {
            val r: DailyReportResponse = reportsService.generateDailyReport(current)
            table.addCell(Paragraph(current.toString(), fontNormal))
            table.addCell(Paragraph(r.breakfastCount.toString(), fontNormal))
            table.addCell(Paragraph(r.lunchCount.toString(), fontNormal))
            table.addCell(Paragraph(r.dinnerCount.toString(), fontNormal))
            table.addCell(Paragraph(r.snackCount.toString(), fontNormal))
            table.addCell(Paragraph(r.specialCount.toString(), fontNormal))
            table.addCell(Paragraph(r.totalCount.toString(), fontNormal))
            table.addCell(Paragraph(r.offlineTransactions.toString(), fontNormal))
            current = current.plusDays(1)
        }

        document.add(table)
        document.add(Paragraph(" "))

        document.add(Paragraph("Администратор: ____________________", fontNormal))
        document.add(Paragraph("Повар:        ____________________", fontNormal))
        document.add(Paragraph("Куратор:      ____________________", fontNormal))

        document.close()
        return baos.toByteArray()
    }
}