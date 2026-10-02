package com.orlauf.rook

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class Period(val title: String) { WEEK("Неделя"), MONTH("Месяц"), YEAR("Год"), ALL("Всё время") }

data class DayTotal(val distanceM: Double, val seconds: Double, val steps: Int, val kcal: Double, val workouts: Int)

data class Bar(val label: String, val distanceM: Double)

data class BestDay(val date: LocalDate, val distanceM: Double)

data class PeriodStats(
    val from: LocalDate,
    val to: LocalDate,
    val workouts: Int,
    val activeDays: Int,
    /** Сколько календарных дней периода уже прошло (включая сегодня). */
    val calendarDays: Int,
    val distanceM: Double,
    val seconds: Double,
    val steps: Long,
    val kcal: Double,
    val bestDay: BestDay?,
    val longest: Workout?,
    val bars: List<Bar>,
) {
    val avgDistancePerWorkout get() = if (workouts > 0) distanceM / workouts else 0.0
    val avgSecondsPerWorkout get() = if (workouts > 0) seconds / workouts else 0.0
    val avgDistancePerDay get() = if (calendarDays > 0) distanceM / calendarDays else 0.0
    val avgStepsPerDay get() = if (calendarDays > 0) steps / calendarDays else 0L
    val avgSpeedKmh get() = if (seconds > 0) distanceM / seconds * 3.6 else 0.0
}

/** Стрики — подряд идущие дни, в которые была хотя бы одна тренировка. */
data class Streaks(val current: Int, val longest: Int)

object Stats {
    private val WEEKDAYS = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    private val MONTHS = listOf("Я", "Ф", "М", "А", "М", "И", "И", "А", "С", "О", "Н", "Д")

    fun dayOf(w: Workout, zone: ZoneId): LocalDate = Instant.ofEpochMilli(w.startMs).atZone(zone).toLocalDate()

    fun byDay(workouts: List<Workout>, zone: ZoneId): Map<LocalDate, DayTotal> =
        workouts.groupBy { dayOf(it, zone) }.mapValues { (_, ws) ->
            DayTotal(
                distanceM = ws.sumOf { it.distanceM },
                seconds = ws.sumOf { it.seconds },
                steps = ws.sumOf { it.steps },
                kcal = ws.sumOf { it.kcal },
                workouts = ws.size,
            )
        }

    fun period(workouts: List<Workout>, period: Period, today: LocalDate, zone: ZoneId): PeriodStats {
        val days = byDay(workouts, zone)
        val from = when (period) {
            Period.WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            Period.MONTH -> today.withDayOfMonth(1)
            Period.YEAR -> today.withDayOfYear(1)
            Period.ALL -> days.keys.minOrNull()?.takeIf { it <= today } ?: today
        }
        val inRange = workouts.filter { dayOf(it, zone) in from..today }
        val daysInRange = days.filterKeys { it in from..today }
        val best = daysInRange.maxByOrNull { it.value.distanceM }

        return PeriodStats(
            from = from,
            to = today,
            workouts = inRange.size,
            activeDays = daysInRange.size,
            calendarDays = (ChronoUnit.DAYS.between(from, today) + 1).toInt(),
            distanceM = inRange.sumOf { it.distanceM },
            seconds = inRange.sumOf { it.seconds },
            steps = inRange.sumOf { it.steps.toLong() },
            kcal = inRange.sumOf { it.kcal },
            bestDay = best?.let { BestDay(it.key, it.value.distanceM) },
            longest = inRange.maxByOrNull { it.seconds },
            bars = bars(period, from, today, days),
        )
    }

    private fun bars(period: Period, from: LocalDate, today: LocalDate, days: Map<LocalDate, DayTotal>): List<Bar> {
        fun dist(d: LocalDate) = days[d]?.distanceM ?: 0.0
        return when (period) {
            Period.WEEK -> (0 until 7).map { i -> Bar(WEEKDAYS[i], dist(from.plusDays(i.toLong()))) }
            Period.MONTH -> (1..today.lengthOfMonth()).map { d -> Bar(d.toString(), dist(today.withDayOfMonth(d))) }
            Period.YEAR -> (1..12).map { m ->
                Bar(MONTHS[m - 1], days.filterKeys { it.year == today.year && it.monthValue == m }.values.sumOf { it.distanceM })
            }
            Period.ALL -> (from.year..today.year).map { y ->
                Bar(y.toString(), days.filterKeys { it.year == y }.values.sumOf { it.distanceM })
            }
        }
    }

    /** Текущий стрик не обрывается, пока сегодняшний день не закончился: считаем от вчера. */
    fun streaks(activeDays: Set<LocalDate>, today: LocalDate): Streaks {
        var current = 0
        var d = if (today in activeDays) today else today.minusDays(1)
        while (d in activeDays) { current++; d = d.minusDays(1) }

        var longest = 0
        var run = 0
        var prev: LocalDate? = null
        for (day in activeDays.sorted()) {
            run = if (prev != null && day == prev.plusDays(1)) run + 1 else 1
            longest = maxOf(longest, run)
            prev = day
        }
        return Streaks(current, longest)
    }
}
