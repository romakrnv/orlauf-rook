package com.orlauf.rook

/** Жёсткие пределы безопасности и пороги. Вес и рост — в Profile (экран настроек). */
object Config {
    /** Диапазон скорости дорожки (из Supported Speed Range), в десятых км/ч. */
    const val MIN_SPEED_TENTHS = 10
    const val MAX_SPEED_TENTHS = 80

    /** Скорость, которая используется как «пауза». */
    const val PAUSE_SPEED_TENTHS = 10

    /** Тренировки короче этого в статистику не попадают. */
    const val MIN_WORKOUT_S = 60.0

    /** Простой дольше этого начинает новую тренировку (даже без «Сброса»). */
    const val WORKOUT_GAP_MS = 30 * 60 * 1000L

    /** Как часто сохранять идущую тренировку на диск. */
    const val SAVE_EVERY_MS = 15_000L
}
