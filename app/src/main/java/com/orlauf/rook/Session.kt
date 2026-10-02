package com.orlauf.rook

/**
 * Собственная сессия приложения. Счётчики дорожки обнуляются при остановках,
 * поэтому дистанцию, время, шаги и калории считаем сами, интегрируя скорость.
 */
class Session(
    var weightKg: Double,
    var strideM: Double,
) {
    var distanceM = 0.0; private set
    var seconds = 0.0; private set
    var kcal = 0.0; private set
    val steps: Int get() = (distanceM / strideM).toInt()

    var paused = false

    private var lastMs: Long? = null

    fun onSpeed(speedKmh: Double, nowMs: Long) {
        val prev = lastMs
        lastMs = nowMs
        if (prev == null || paused || speedKmh <= 0.0) return
        // Ограничиваем шаг: после разрыва связи не накручиваем лишнее.
        val dt = ((nowMs - prev) / 1000.0).coerceIn(0.0, 3.0)
        distanceM += speedKmh / 3.6 * dt
        seconds += dt
        // ACSM, ходьба: VO2 = 0.1 * v(м/мин) + 3.5 мл/кг/мин; 1 л O2 ~ 5 ккал.
        val vo2 = 0.1 * (speedKmh * 1000.0 / 60.0) + 3.5
        kcal += vo2 * weightKg / 1000.0 * 5.0 * (dt / 60.0)
    }

    fun reset() {
        distanceM = 0.0; seconds = 0.0; kcal = 0.0
        paused = false; lastMs = null
    }
}
