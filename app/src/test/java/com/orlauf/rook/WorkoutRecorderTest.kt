package com.orlauf.rook

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WorkoutRecorderTest {

    private val recorder = WorkoutRecorder(strideM = 1.0, gapMs = 60_000)

    @Test
    fun `workout counts growth since its start`() {
        recorder.update(distanceM = 10.0, seconds = 5.0, kcal = 1.0, nowMs = 1_000)
        recorder.update(distanceM = 30.0, seconds = 15.0, kcal = 3.0, nowMs = 11_000)

        assertThat(recorder.current).isEqualTo(Workout(1_000, 11_000, 30.0, 15.0, 3.0, 30))
    }

    @Test
    fun `pause without growth keeps the same workout`() {
        recorder.update(10.0, 5.0, 1.0, 1_000)
        recorder.update(10.0, 5.0, 1.0, 30_000)
        val newStarted = recorder.update(20.0, 10.0, 2.0, 50_000)

        assertThat(newStarted).isFalse()
        assertThat(recorder.current?.startMs).isEqualTo(1_000)
        assertThat(recorder.current?.distanceM).isEqualTo(20.0)
    }

    @Test
    fun `long idle starts a new workout from current session values`() {
        recorder.update(100.0, 50.0, 5.0, 1_000)
        recorder.update(100.0, 50.0, 5.0, 100_000)
        val newStarted = recorder.update(110.0, 55.0, 6.0, 101_000)

        assertThat(newStarted).isTrue()
        assertThat(recorder.current).isEqualTo(Workout(101_000, 101_000, 10.0, 5.0, 1.0, 10))
    }

    @Test
    fun `reset starts a new workout`() {
        recorder.update(100.0, 50.0, 5.0, 1_000)
        recorder.reset()
        recorder.update(10.0, 5.0, 1.0, 2_000)

        assertThat(recorder.current).isEqualTo(Workout(2_000, 2_000, 10.0, 5.0, 1.0, 10))
    }

    @Test
    fun `nothing is recorded before movement`() {
        recorder.update(0.0, 0.0, 0.0, 1_000)

        assertThat(recorder.current).isNull()
    }
}
