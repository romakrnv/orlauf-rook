package com.orlauf.rook

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

class ProfileTest {

    @Test
    fun `stride is estimated from height when not set`() {
        assertThat(Profile(weightKg = 70.0, heightCm = 200.0).strideM).isCloseTo(0.83, within(1e-9))
    }

    @Test
    fun `explicit stride wins over height`() {
        assertThat(Profile(weightKg = 70.0, heightCm = 200.0, strideCm = 75.0).strideM).isCloseTo(0.75, within(1e-9))
    }

    @Test
    fun `parse accepts comma and blank stride`() {
        assertThat(Profile.parse("70,5", " 175 ", "")).isEqualTo(Profile(70.5, 175.0, null))
    }

    @Test
    fun `parse reads explicit stride`() {
        assertThat(Profile.parse("70", "175", "72.5")).isEqualTo(Profile(70.0, 175.0, 72.5))
    }

    @Test
    fun `parse rejects garbage and out of range values`() {
        assertThat(Profile.parse("abc", "175", "")).isNull()
        assertThat(Profile.parse("10", "175", "")).isNull()
        assertThat(Profile.parse("70", "300", "")).isNull()
        assertThat(Profile.parse("70", "175", "5")).isNull()
    }
}
