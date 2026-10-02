package com.orlauf.rook

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

class JourneyTest {

    private val first = Route("A", "", "", listOf(Stop("a0", 0.0, ""), Stop("a1", 10.0, ""), Stop("a2", 30.0, "")))
    private val second = Route("B", "", "", listOf(Stop("b0", 0.0, ""), Stop("b1", 50.0, "")))
    private val routes = listOf(first, second)

    @Test
    fun `at the start only the first stop is reached`() {
        val s = Journey.state(0.0, routes)

        assertThat(s.current).isEqualTo(first)
        assertThat(s.lastStop?.name).isEqualTo("a0")
        assertThat(s.nextStop?.name).isEqualTo("a1")
        assertThat(s.kmToNext).isEqualTo(10.0)
        assertThat(s.reachedCount).isEqualTo(1)
    }

    @Test
    fun `progress inside a route`() {
        val s = Journey.state(15.0, routes)

        assertThat(s.lastStop?.name).isEqualTo("a1")
        assertThat(s.nextStop?.name).isEqualTo("a2")
        assertThat(s.kmToNext).isEqualTo(15.0)
        assertThat(s.routeProgress).isCloseTo(0.5, within(1e-9))
    }

    @Test
    fun `finishing a route opens the next one`() {
        val s = Journey.state(30.0, routes)

        assertThat(s.completed).containsExactly(first)
        assertThat(s.current).isEqualTo(second)
        assertThat(s.lastStop?.name).isEqualTo("b0")
        // a0, a1, a2 и сразу b0
        assertThat(s.reachedCount).isEqualTo(4)
    }

    @Test
    fun `everything completed`() {
        val s = Journey.state(1000.0, routes)

        assertThat(s.completed).containsExactly(first, second)
        assertThat(s.current).isNull()
        assertThat(s.nextStop).isNull()
        assertThat(s.reachedCount).isEqualTo(5)
    }

    @Test
    fun `reached stops come in order across routes`() {
        assertThat(Journey.reached(35.0, routes).map { it.stop.name }).containsExactly("a0", "a1", "a2", "b0")
    }

    @Test
    fun `real routes have increasing stops starting at zero`() {
        Journey.ROUTES.forEach { r ->
            assertThat(r.stops.first().km).`as`(r.title).isZero()
            assertThat(r.stops.map { it.km }).`as`(r.title).isSorted()
            assertThat(r.stops.map { it.quote }).`as`(r.title).allMatch { it.isNotBlank() }
        }
    }
}
