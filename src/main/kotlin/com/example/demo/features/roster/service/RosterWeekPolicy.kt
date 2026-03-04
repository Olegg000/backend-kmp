package com.example.demo.features.roster.service

import org.springframework.stereotype.Component
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

@Component
class RosterWeekPolicy(
    private val businessClock: Clock,
) {
    fun now(): LocalDateTime = LocalDateTime.now(businessClock)

    fun today(): LocalDate = LocalDate.now(businessClock)

    fun weekStart(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun nextWeekStart(referenceDate: LocalDate = today()): LocalDate =
        weekStart(referenceDate).plusWeeks(1)

    fun weekDates(weekStart: LocalDate): List<LocalDate> = (0..4).map { weekStart.plusDays(it.toLong()) }

    fun isWeekday(date: LocalDate): Boolean = date.dayOfWeek in DayOfWeek.MONDAY..DayOfWeek.FRIDAY

    fun deadlineForWeek(weekStart: LocalDate): LocalDateTime {
        val previousFriday = weekStart.minusDays(3)
        return previousFriday.atTime(12, 0)
    }

    fun isLockedWeek(weekStart: LocalDate, at: LocalDateTime = now()): Boolean {
        val nextWeek = nextWeekStart(at.toLocalDate())
        if (weekStart != nextWeek) return false
        return !at.isBefore(deadlineForWeek(weekStart))
    }

    fun isDateEditable(date: LocalDate, at: LocalDateTime = now()): Boolean {
        if (!isWeekday(date)) return false
        val nextWeek = nextWeekStart(at.toLocalDate())
        val targetWeek = weekStart(date)
        if (targetWeek.isBefore(nextWeek)) return false
        if (targetWeek == nextWeek && isLockedWeek(targetWeek, at)) return false
        return true
    }
}
