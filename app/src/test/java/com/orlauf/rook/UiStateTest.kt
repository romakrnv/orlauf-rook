package com.orlauf.rook

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UiStateTest {

    private val running = UiState(link = Link.READY, speedKmh = 3.5, speedKnown = true)

    @Test
    fun `power off is allowed while belt is moving`() {
        assertThat(running.canPowerOff).isTrue()
    }

    @Test
    fun `power off is allowed on pause speed`() {
        assertThat(running.copy(speedKmh = 1.0, paused = true).canPowerOff).isTrue()
    }

    @Test
    fun `power off is blocked when belt stands still`() {
        assertThat(running.copy(speedKmh = 0.0).canPowerOff).isFalse()
    }

    @Test
    fun `power off is blocked during countdown`() {
        assertThat(running.copy(pending = BeltCommand.POWER_OFF, pendingLeft = 2).canPowerOff).isFalse()
    }

    @Test
    fun `start is allowed only when belt stands still`() {
        assertThat(running.copy(speedKmh = 0.0).canStart).isTrue()
        assertThat(running.canStart).isFalse()
    }

    @Test
    fun `start is blocked during countdown`() {
        assertThat(running.copy(speedKmh = 0.0, pending = BeltCommand.START, pendingLeft = 3).canStart).isFalse()
    }

    @Test
    fun `nothing is allowed without connection`() {
        val idle = UiState(link = Link.IDLE)
        assertThat(idle.canStart).isFalse()
        assertThat(idle.canPowerOff).isFalse()
    }

    @Test
    fun `power off is blocked without connection`() {
        assertThat(running.copy(link = Link.LOST).canPowerOff).isFalse()
    }

    @Test
    fun `before first packet after connect nothing is allowed`() {
        val justConnected = UiState(link = Link.READY, speedKnown = false)
        assertThat(justConnected.canStart).isFalse()
        assertThat(justConnected.canPowerOff).isFalse()
        assertThat(justConnected.canChangeSpeed).isFalse()
    }

    @Test
    fun `speed can be changed only on moving belt and not on pause`() {
        assertThat(running.canChangeSpeed).isTrue()
        assertThat(running.copy(speedKmh = 0.0).canChangeSpeed).isFalse()
        assertThat(running.copy(speedKmh = 1.0, paused = true).canChangeSpeed).isFalse()
    }
}
