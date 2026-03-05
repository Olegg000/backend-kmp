package com.example.demo.features.reports.service

import com.example.demo.core.database.NoMealReasonType
import com.example.demo.features.reports.dto.AssignedByRoleFilter
import com.example.demo.features.reports.dto.ReportAccessScope
import com.example.demo.core.database.StudentCategory
import com.lowagie.text.Document
import com.lowagie.text.Font
import com.lowagie.text.PageSize
import com.lowagie.text.Paragraph
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

    fun generateConsumptionPdf(
        currentLogin: String,
        startDate: LocalDate,
        endDate: LocalDate,
        groupId: Int?,
        assignedByRoleFilter: AssignedByRoleFilter,
        accessScope: ReportAccessScope = ReportAccessScope.AUTO,
    ): ByteArray {
        val rows = reportsService.generateConsumptionReport(
            currentLogin = currentLogin,
            startDate = startDate,
            endDate = endDate,
            groupId = groupId,
            assignedByRoleFilter = assignedByRoleFilter,
            accessScope = accessScope,
        )
        val totals = reportsService.calculateReportTotals(rows)

        val baos = ByteArrayOutputStream()
        val document = Document(PageSize.A4.rotate())
        PdfWriter.getInstance(document, baos)
        document.open()

        val fontHeader = Font(Font.HELVETICA, 14f, Font.BOLD)
        val fontNormal = Font(Font.HELVETICA, 8f, Font.NORMAL)

        document.add(Paragraph("Отчёт по питанию за период $startDate — $endDate", fontHeader))
        document.add(Paragraph(" "))

        val table = PdfPTable(14)
        table.widthPercentage = 100f
        table.setWidths(
            floatArrayOf(
                1.25f, // Дата
                1.45f, // Группа
                2.15f, // Студент
                1.1f, // Категория
                2.1f, // Куратор
                2.2f, // Статус/Причина
                1.5f, // Период
                1.8f, // Комментарий
                0.95f, // Завтрак
                1.0f, // Tx завтрак
                2.0f, // Сканировал завтрак
                0.95f, // Обед
                1.0f, // Tx обед
                2.0f // Сканировал обед
            )
        )

        fun headerCell(text: String): PdfPCell =
            PdfPCell(Paragraph(text, fontNormal)).apply { backgroundColor = Color.LIGHT_GRAY }

        table.addCell(headerCell("Дата"))
        table.addCell(headerCell("Группа"))
        table.addCell(headerCell("Студент"))
        table.addCell(headerCell("Категория"))
        table.addCell(headerCell("Куратор"))
        table.addCell(headerCell("Статус/Причина"))
        table.addCell(headerCell("Период"))
        table.addCell(headerCell("Комментарий"))
        table.addCell(headerCell("Завтрак"))
        table.addCell(headerCell("TX завтрак"))
        table.addCell(headerCell("Скан. завтрак"))
        table.addCell(headerCell("Обед"))
        table.addCell(headerCell("TX обед"))
        table.addCell(headerCell("Скан. обед"))

        rows.forEach {
            table.addCell(Paragraph(it.date.toString(), fontNormal))
            table.addCell(Paragraph(it.groupName, fontNormal))
            table.addCell(Paragraph(it.studentName, fontNormal))
            table.addCell(Paragraph(studentCategoryTitleRu(it.category), fontNormal))
            table.addCell(
                Paragraph(
                    buildAssignedByLabel(
                        assignedByName = it.assignedByName
                    ),
                    fontNormal
                )
            )
            table.addCell(
                Paragraph(
                    buildNoMealStatusSummary(
                        reasonType = it.noMealReasonType,
                        reasonText = it.noMealReasonText,
                    ),
                    fontNormal
                )
            )
            table.addCell(
                Paragraph(
                    buildAbsencePeriodSummary(
                        absenceFrom = it.absenceFrom,
                        absenceTo = it.absenceTo,
                    ),
                    fontNormal
                )
            )
            table.addCell(Paragraph(it.comment ?: "-", fontNormal))
            table.addCell(Paragraph(if (it.breakfastUsed) "Да" else "Нет", fontNormal))
            table.addCell(Paragraph(it.breakfastTransactionId?.toString() ?: "-", fontNormal))
            table.addCell(Paragraph(it.breakfastScannedByName ?: "-", fontNormal))
            table.addCell(Paragraph(if (it.lunchUsed) "Да" else "Нет", fontNormal))
            table.addCell(Paragraph(it.lunchTransactionId?.toString() ?: "-", fontNormal))
            table.addCell(Paragraph(it.lunchScannedByName ?: "-", fontNormal))
        }

        document.add(table)
        document.add(Paragraph(" "))
        document.add(Paragraph("ИТОГИ", fontHeader))
        document.add(Paragraph(" "))
        document.add(buildTotalsTable(totals, fontNormal))
        document.close()
        return baos.toByteArray()
    }

    private fun buildTotalsTable(totals: ReportTotals, fontNormal: Font): PdfPTable {
        val table = PdfPTable(4)
        table.widthPercentage = 100f
        table.setWidths(floatArrayOf(1.7f, 3.2f, 1.2f, 2.2f))

        fun headerCell(text: String): PdfPCell =
            PdfPCell(Paragraph(text, fontNormal)).apply { backgroundColor = Color.LIGHT_GRAY }

        fun addRow(block: String, metric: String, value: Int, hint: String) {
            table.addCell(Paragraph(block, fontNormal))
            table.addCell(Paragraph(metric, fontNormal))
            table.addCell(Paragraph(value.toString(), fontNormal))
            table.addCell(Paragraph(hint, fontNormal))
        }

        table.addCell(headerCell("Блок"))
        table.addCell(headerCell("Метрика"))
        table.addCell(headerCell("Значение"))
        table.addCell(headerCell("Пояснение"))

        addRow("СВОДКА", "Куратор отметил (план): только завтрак", totals.plannedOnlyBreakfastCount, "Строки с планом только на завтрак")
        addRow("СВОДКА", "Куратор отметил (план): только обед", totals.plannedOnlyLunchCount, "Строки с планом только на обед")
        addRow("СВОДКА", "Куратор отметил (план): завтрак и обед", totals.plannedBothCount, "Строки с двумя планами")
        addRow("СВОДКА", "Было: только завтрак", totals.usedOnlyBreakfastCount, "Только синхронизированные серверные транзакции")
        addRow("СВОДКА", "Было: только обед", totals.usedOnlyLunchCount, "Только синхронизированные серверные транзакции")
        addRow("СВОДКА", "Было: завтрак и обед", totals.usedBothCount, "Только синхронизированные серверные транзакции")
        addRow("СВОДКА", "Куратор отметил, но факта питания нет: только завтрак", totals.plannedNoFactOnlyBreakfastCount, "План был, факт не зафиксирован")
        addRow("СВОДКА", "Куратор отметил, но факта питания нет: только обед", totals.plannedNoFactOnlyLunchCount, "План был, факт не зафиксирован")
        addRow("СВОДКА", "Куратор отметил, но факта питания нет: завтрак и обед", totals.plannedNoFactBothCount, "План был, факта по обоим приемам нет")
        addRow("СВОДКА", "Частичный факт при плане «завтрак и обед»", totals.plannedPartialBothCount, "Есть факт только по одному из приемов")
        addRow("СВОДКА", "Не отмечено куратором / иные причины", totals.notMarkedRowsCount, "Нет плана ни на завтрак, ни на обед")
        addRow("СВОДКА", "Причины: отчислен", totals.reasonExpelledCount, "Количество строк")
        addRow("СВОДКА", "Причины: больничный", totals.reasonSickLeaveCount, "Количество строк")
        addRow("СВОДКА", "Причины: куратор не заполнил табель", totals.reasonMissingRosterCount, "Количество строк")
        addRow("СВОДКА", "Причины: иное", totals.reasonOtherCount, "Количество строк")
        addRow("СВОДКА", "Строк в детализации", totals.totalRowsCount, "Всего строк в отчетной таблице")

        return table
    }

    private fun studentCategoryTitleRu(category: StudentCategory?): String = when (category) {
        null -> "-"
        StudentCategory.SVO -> "СВО"
        StudentCategory.MANY_CHILDREN -> "Многодетные"
    }

    private fun noMealReasonTypeTitleRu(reasonType: NoMealReasonType?): String = when (reasonType) {
        null -> "-"
        NoMealReasonType.EXPELLED -> "Отчислен"
        NoMealReasonType.SICK_LEAVE -> "Больничный"
        NoMealReasonType.OTHER -> "Иное"
        NoMealReasonType.MISSING_ROSTER -> "Куратор не заполнил табель"
    }

    private fun buildNoMealStatusSummary(reasonType: NoMealReasonType?, reasonText: String?): String {
        val status = noMealReasonTypeTitleRu(reasonType)
        val text = reasonText?.trim().orEmpty()
        if (status == "-" && text.isBlank()) return "-"
        if (status == "-") return text
        if (reasonType == NoMealReasonType.OTHER && text.isNotBlank()) return text
        return status
    }

    private fun buildAbsencePeriodSummary(absenceFrom: LocalDate?, absenceTo: LocalDate?): String {
        if (absenceFrom == null && absenceTo == null) return "-"
        val from = absenceFrom?.toString() ?: "?"
        val to = absenceTo?.toString() ?: "?"
        return "$from - $to"
    }

    private fun buildAssignedByLabel(assignedByName: String?): String =
        assignedByName?.takeIf { it.isNotBlank() } ?: "Не назначен"
}
