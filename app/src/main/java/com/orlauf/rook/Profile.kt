package com.orlauf.rook

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Параметры пользователя для расчёта калорий и шагов. Хранятся только на телефоне.
 * strideCm == null — длина шага оценивается по росту.
 */
data class Profile(
    val weightKg: Double = DEFAULT_WEIGHT_KG,
    val heightCm: Double = DEFAULT_HEIGHT_CM,
    val strideCm: Double? = null,
) {
    val strideM: Double get() = (strideCm ?: heightCm * STRIDE_PER_HEIGHT) / 100.0

    companion object {
        const val DEFAULT_WEIGHT_KG = 70.0
        const val DEFAULT_HEIGHT_CM = 170.0

        /** Грубая оценка длины шага при ходьбе: рост * 0.415. */
        const val STRIDE_PER_HEIGHT = 0.415

        val WEIGHT_RANGE = 30.0..250.0
        val HEIGHT_RANGE = 100.0..250.0
        val STRIDE_RANGE = 30.0..150.0

        /** Разбор ввода из формы. Запятая допускается. Пустой шаг — авто. null, если что-то вне диапазона. */
        fun parse(weight: String, height: String, stride: String): Profile? {
            fun num(s: String) = s.trim().replace(',', '.').toDoubleOrNull()
            val w = num(weight)?.takeIf { it in WEIGHT_RANGE } ?: return null
            val h = num(height)?.takeIf { it in HEIGHT_RANGE } ?: return null
            val st = if (stride.isBlank()) null else num(stride)?.takeIf { it in STRIDE_RANGE } ?: return null
            return Profile(w, h, st)
        }
    }
}

class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)

    private val _profile = MutableStateFlow(load())
    val profile: StateFlow<Profile> = _profile

    /** Пользователь ещё не вводил свои данные — считаем по значениям по умолчанию. */
    val isSet: Boolean get() = prefs.contains(KEY_WEIGHT)

    fun save(p: Profile) {
        prefs.edit()
            .putFloat(KEY_WEIGHT, p.weightKg.toFloat())
            .putFloat(KEY_HEIGHT, p.heightCm.toFloat())
            .apply { if (p.strideCm != null) putFloat(KEY_STRIDE, p.strideCm.toFloat()) else remove(KEY_STRIDE) }
            .apply()
        _profile.value = p
    }

    private fun load() = Profile(
        weightKg = prefs.getFloat(KEY_WEIGHT, Profile.DEFAULT_WEIGHT_KG.toFloat()).toDouble(),
        heightCm = prefs.getFloat(KEY_HEIGHT, Profile.DEFAULT_HEIGHT_CM.toFloat()).toDouble(),
        strideCm = if (prefs.contains(KEY_STRIDE)) prefs.getFloat(KEY_STRIDE, 0f).toDouble() else null,
    )

    private companion object {
        const val KEY_WEIGHT = "weight_kg"
        const val KEY_HEIGHT = "height_cm"
        const val KEY_STRIDE = "stride_cm"
    }
}
