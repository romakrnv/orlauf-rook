package com.orlauf.rook

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

data class UiState(
    val link: Link = Link.IDLE,
    val speedKmh: Double = 0.0,
    val distanceM: Double = 0.0,
    val seconds: Int = 0,
    val steps: Int = 0,
    val kcal: Double = 0.0,
    val paused: Boolean = false,
    val message: String? = null,
    /** Вес и рост ещё не введены — калории и шаги считаются по умолчанию. */
    val profileMissing: Boolean = false,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val ble = RookBle(app)
    private val profiles = ProfileStore(app)
    val profile: StateFlow<Profile> = profiles.profile

    private val session = Session(profile.value.weightKg, profile.value.strideM)
    private val recorder = WorkoutRecorder(profile.value.strideM)
    private val store = WorkoutStore(File(app.filesDir, "workouts.csv"))
    private var lastSaveMs = 0L

    /** История тренировок для экрана статистики (включая идущую). */
    val workouts: StateFlow<List<Workout>> = store.items

    private val _ui = MutableStateFlow(UiState(profileMissing = !profiles.isSet))
    val ui: StateFlow<UiState> = _ui

    /** Целевая скорость в десятых км/ч, которую мы последний раз просили у дорожки. */
    private var targetTenths = Config.MIN_SPEED_TENTHS
    private var speedBeforePause = Config.MIN_SPEED_TENTHS

    init {
        viewModelScope.launch { ble.link.collect { l -> _ui.value = _ui.value.copy(link = l) } }
        viewModelScope.launch { ble.messages.collect { m -> _ui.value = _ui.value.copy(message = m) } }
        viewModelScope.launch {
            ble.data.collect { d ->
                val now = System.currentTimeMillis()
                session.onSpeed(d.speedKmh, now)
                record(now)
                // Пока команд не было, подхватываем фактическую скорость (например, после пульта).
                val tenths = Math.round(d.speedKmh * 10).toInt()
                if (d.speedKmh > 0 && !session.paused && tenths in Config.MIN_SPEED_TENTHS..Config.MAX_SPEED_TENTHS) {
                    targetTenths = tenths
                }
                publish(d.speedKmh)
            }
        }
    }

    private fun record(now: Long) {
        val newWorkout = recorder.update(session.distanceM, session.seconds, session.kcal, now)
        recorder.current?.takeIf { it.seconds >= Config.MIN_WORKOUT_S }?.let(store::put)
        if (newWorkout || now - lastSaveMs >= Config.SAVE_EVERY_MS) {
            lastSaveMs = now
            viewModelScope.launch(Dispatchers.IO) { store.persist() }
        }
    }

    /** Сохранить статистику на диск сразу (сворачивание, пауза, сброс). */
    fun saveStats() = store.persist()

    private fun publish(speed: Double = _ui.value.speedKmh) {
        _ui.value = _ui.value.copy(
            speedKmh = speed,
            distanceM = session.distanceM,
            seconds = session.seconds.toInt(),
            steps = session.steps,
            kcal = session.kcal,
            paused = session.paused,
        )
    }

    fun saveProfile(p: Profile) {
        profiles.save(p)
        session.weightKg = p.weightKg
        session.strideM = p.strideM
        recorder.strideM = p.strideM
        _ui.value = _ui.value.copy(profileMissing = false)
        publish()
    }

    fun connect() = ble.connect()

    fun start() {
        session.paused = false
        ble.start()
        publish()
    }

    fun faster() = changeSpeed(+1)
    fun slower() = changeSpeed(-1)

    private fun changeSpeed(deltaTenths: Int) {
        if (session.paused) return
        targetTenths = (targetTenths + deltaTenths)
            .coerceIn(Config.MIN_SPEED_TENTHS, Config.MAX_SPEED_TENTHS)
        ble.setSpeedTenths(targetTenths)
    }

    /** «Пауза» = минимальная скорость 1.0 км/ч, счётчики приложения замирают. */
    fun togglePause() {
        if (!session.paused) {
            speedBeforePause = targetTenths
            session.paused = true
            ble.setSpeedTenths(Config.PAUSE_SPEED_TENTHS)
            saveStats()
        } else {
            session.paused = false
            targetTenths = speedBeforePause
            ble.setSpeedTenths(targetTenths)
        }
        publish()
    }

    fun resetSession() {
        saveStats()
        session.reset()
        recorder.reset()
        publish()
    }

    /** Полное выключение дорожки (включить обратно можно только кнопкой). */
    fun powerOff() = ble.powerOff()

    fun dismissMessage() { _ui.value = _ui.value.copy(message = null) }

    override fun onCleared() {
        saveStats()
        ble.close()
    }
}
