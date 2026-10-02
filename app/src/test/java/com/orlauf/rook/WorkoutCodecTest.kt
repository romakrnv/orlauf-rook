package com.orlauf.rook

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WorkoutCodecTest {

    @Test
    fun `encode and decode round trip`() {
        val ws = listOf(
            Workout(1_000, 61_000, 1234.5, 600.0, 45.25, 1582),
            Workout(2_000, 62_000, 10.0, 60.0, 1.5, 12),
        )

        assertThat(WorkoutCodec.decode(WorkoutCodec.encode(ws))).isEqualTo(ws)
    }

    @Test
    fun `broken lines are skipped and result is sorted`() {
        val text = "2000;3000;1.0;2.0;3.0;4\ngarbage\n1;2;x;4;5;6\n1000;2000;1.0;2.0;3.0;4\n"

        assertThat(WorkoutCodec.decode(text).map { it.startMs }).containsExactly(1000L, 2000L)
    }
}
