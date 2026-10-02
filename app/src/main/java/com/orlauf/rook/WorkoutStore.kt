package com.orlauf.rook

import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Locale

/** Одна завершённая (или идущая) тренировка. Ключ — время начала. */
data class Workout(
    val startMs: Long,
    val endMs: Long,
    val distanceM: Double,
    val seconds: Double,
    val kcal: Double,
    val steps: Int,
)

/**
 * Режет непрерывную сессию на тренировки. Сессия считает накопительно,
 * тренировка хранит только прирост с момента своего начала.
 * Новая тренировка начинается после reset() или после простоя дольше gapMs.
 */
class WorkoutRecorder(
    var strideM: Double,
    private val gapMs: Long = Config.WORKOUT_GAP_MS,
) {
    private var prevDistance = 0.0
    private var prevSeconds = 0.0
    private var prevKcal = 0.0

    private var baseDistance = 0.0
    private var baseSeconds = 0.0
    private var baseKcal = 0.0

    private var startMs: Long? = null
    private var lastActiveMs = 0L

    var current: Workout? = null; private set

    /** Возвращает true, если началась новая тренировка, а предыдущая закончилась. */
    fun update(distanceM: Double, seconds: Double, kcal: Double, nowMs: Long): Boolean {
        var finishedPrevious = false
        if (seconds > prevSeconds) {
            if (startMs == null || nowMs - lastActiveMs > gapMs) {
                finishedPrevious = current != null
                startMs = nowMs
                baseDistance = prevDistance; baseSeconds = prevSeconds; baseKcal = prevKcal
            }
            lastActiveMs = nowMs
            val dist = distanceM - baseDistance
            current = Workout(
                startMs = startMs!!,
                endMs = nowMs,
                distanceM = dist,
                seconds = seconds - baseSeconds,
                kcal = kcal - baseKcal,
                steps = (dist / strideM).toInt(),
            )
        }
        prevDistance = distanceM; prevSeconds = seconds; prevKcal = kcal
        return finishedPrevious
    }

    /** Вызывать вместе с Session.reset(). */
    fun reset() {
        prevDistance = 0.0; prevSeconds = 0.0; prevKcal = 0.0
        startMs = null; current = null
    }
}

/** Текстовый формат: строка на тренировку, `startMs;endMs;distanceM;seconds;kcal;steps`. */
object WorkoutCodec {
    fun encode(items: List<Workout>): String = buildString {
        for (w in items) {
            append(w.startMs).append(';')
            append(w.endMs).append(';')
            append(String.format(Locale.ROOT, "%.1f", w.distanceM)).append(';')
            append(String.format(Locale.ROOT, "%.1f", w.seconds)).append(';')
            append(String.format(Locale.ROOT, "%.2f", w.kcal)).append(';')
            append(w.steps).append('\n')
        }
    }

    /** Битые строки пропускаем, чтобы одна ошибка не стёрла всю историю. */
    fun decode(text: String): List<Workout> = text.lineSequence().mapNotNull { line ->
        val p = line.trim().split(';')
        if (p.size < 6) return@mapNotNull null
        try {
            Workout(p[0].toLong(), p[1].toLong(), p[2].toDouble(), p[3].toDouble(), p[4].toDouble(), p[5].toInt())
        } catch (e: NumberFormatException) {
            null
        }
    }.sortedBy { it.startMs }.toList()
}

/**
 * История тренировок во внутренней памяти приложения (filesDir).
 * В памяти — все, включая только что начатую (чтобы статистика обновлялась сразу).
 * На диск — только не короче minSeconds: случайные короткие включения в историю не попадают.
 */
class WorkoutStore(file: File, private val minSeconds: Double = Config.MIN_WORKOUT_S) {
    private val atomic = AtomicFile(file)

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<Workout>> = _items

    /** Добавить или обновить тренировку в памяти. На диск — через persist(). */
    fun put(w: Workout) {
        _items.update { list ->
            val i = list.indexOfLast { it.startMs == w.startMs }
            if (i >= 0) list.toMutableList().also { it[i] = w } else list + w
        }
    }

    /** То, что попадает на диск и в резервную копию. */
    fun saved(): List<Workout> = _items.value.filter { it.seconds >= minSeconds }

    /** Заменить всю историю (восстановление из резервной копии). */
    fun replace(items: List<Workout>) {
        _items.value = items.sortedBy { it.startMs }
        persist()
    }

    /** Выкинуть из памяти законченные короткие тренировки. Идущую (keepStartMs) оставить. */
    fun pruneShort(keepStartMs: Long?) {
        _items.update { list -> list.filter { it.seconds >= minSeconds || it.startMs == keepStartMs } }
    }

    @Synchronized
    fun persist() {
        val bytes = WorkoutCodec.encode(saved()).toByteArray()
        val out = try { atomic.startWrite() } catch (e: IOException) { return }
        try {
            out.write(bytes)
            atomic.finishWrite(out)
        } catch (e: IOException) {
            atomic.failWrite(out)
        }
    }

    private fun load(): List<Workout> = try {
        WorkoutCodec.decode(String(atomic.readFully()))
    } catch (e: FileNotFoundException) {
        emptyList()
    } catch (e: IOException) {
        emptyList()
    }
}
