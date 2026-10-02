package com.orlauf.rook

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset

class StatsTest {

    private val zone = ZoneOffset.UTC

    // Четверг
    private val today = LocalDate.of(2026, 10, 1)

    private fun workout(date: LocalDate, distanceM: Double = 1000.0, seconds: Double = 600.0, hour: Int = 10): Workout {
        val start = date.atTime(hour, 0).toInstant(zone).toEpochMilli()
        return Workout(start, start + (seconds * 1000).toLong(), distanceM, seconds, 50.0, (distanceM / 0.7).toInt())
    }

    @Nested
    inner class PeriodStats {

        @Test
        fun `week starts on monday and averages over elapsed days`() {
            val ws = listOf(
                workout(LocalDate.of(2026, 9, 28), distanceM = 2000.0), // пн
                workout(LocalDate.of(2026, 9, 30), distanceM = 1000.0), // ср
                workout(LocalDate.of(2026, 9, 27), distanceM = 5000.0), // вс прошлой недели
            )

            val s = Stats.period(ws, Period.WEEK, today, zone)

            assertThat(s.from).isEqualTo(LocalDate.of(2026, 9, 28))
            assertThat(s.workouts).isEqualTo(2)
            assertThat(s.activeDays).isEqualTo(2)
            assertThat(s.calendarDays).isEqualTo(4)
            assertThat(s.distanceM).isEqualTo(3000.0)
            assertThat(s.avgDistancePerDay).isEqualTo(750.0)
            assertThat(s.avgDistancePerWorkout).isEqualTo(1500.0)
            assertThat(s.bars.map { it.distanceM }).containsExactly(2000.0, 0.0, 1000.0, 0.0, 0.0, 0.0, 0.0)
        }

        @Test
        fun `two workouts on one day make one active day and one best day`() {
            val day = LocalDate.of(2026, 9, 29)
            val ws = listOf(workout(day, 1000.0, hour = 8), workout(day, 1500.0, hour = 19))

            val s = Stats.period(ws, Period.WEEK, today, zone)

            assertThat(s.workouts).isEqualTo(2)
            assertThat(s.activeDays).isEqualTo(1)
            assertThat(s.bestDay).isEqualTo(BestDay(day, 2500.0))
        }

        @Test
        fun `all time starts from first workout and groups bars by year`() {
            val ws = listOf(workout(LocalDate.of(2025, 12, 31)), workout(LocalDate.of(2026, 1, 1)))

            val s = Stats.period(ws, Period.ALL, today, zone)

            assertThat(s.from).isEqualTo(LocalDate.of(2025, 12, 31))
            assertThat(s.bars.map { it.label }).containsExactly("2025", "2026")
            assertThat(s.bars.map { it.distanceM }).containsExactly(1000.0, 1000.0)
        }

        @Test
        fun `longest workout and average speed`() {
            val ws = listOf(
                workout(today, distanceM = 1000.0, seconds = 600.0),
                workout(today.minusDays(1), distanceM = 3000.0, seconds = 1800.0),
            )

            val s = Stats.period(ws, Period.YEAR, today, zone)

            assertThat(s.longest?.seconds).isEqualTo(1800.0)
            assertThat(s.avgSpeedKmh).isCloseTo(6.0, within(1e-9))
        }

        @Test
        fun `empty history gives zeros`() {
            val s = Stats.period(emptyList(), Period.ALL, today, zone)

            assertThat(s.workouts).isZero()
            assertThat(s.avgDistancePerWorkout).isZero()
            assertThat(s.bestDay).isNull()
        }
    }

    @Nested
    inner class StreakCount {

        @Test
        fun `current streak survives until today ends`() {
            val days = setOf(today.minusDays(1), today.minusDays(2), today.minusDays(3))

            assertThat(Stats.streaks(days, today).current).isEqualTo(3)
        }

        @Test
        fun `current streak includes today`() {
            val days = setOf(today, today.minusDays(1))

            assertThat(Stats.streaks(days, today).current).isEqualTo(2)
        }

        @Test
        fun `missed yesterday breaks current streak`() {
            val days = setOf(today.minusDays(2), today.minusDays(3))

            assertThat(Stats.streaks(days, today).current).isZero()
        }

        @Test
        fun `longest streak is found in history`() {
            val days = setOf(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 3), LocalDate.of(2026, 1, 4),
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 2),
                today,
            )

            assertThat(Stats.streaks(days, today)).isEqualTo(Streaks(current = 1, longest = 4))
        }
    }
}
