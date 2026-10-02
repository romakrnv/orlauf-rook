package com.orlauf.rook

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Команда, после которой кнопка Start / Power off заблокирована на время отсчёта дорожки. */
enum class BeltCommand { START, POWER_OFF }

data class UiState(
    val link: Link = Link.IDLE,
    val speedKmh: Double = 0.0,
    val distanceM: Double = 0.0,
    val seconds: Int = 0,
    val steps: Int = 0,
    val kcal: Double = 0.0,
    val paused: Boolean = false,
    val message: String? = null,
    /** Первый пользователь ещё не настроен — калории и шаги считаются по умолчанию. */
    val needsSetup: Boolean = false,
    /**
     * Пришёл ли с дорожки хоть один пакет после подключения. До этого скорость неизвестна:
     * лента могла быть запущена с пульта или, наоборот, выключена, пока не было связи.
     */
    val speedKnown: Boolean = false,
    /** Только что достигнутые точки маршрута: показываются карточкой с цитатой по одной. */
    val milestones: List<Reached> = emptyList(),
    /** Только что отправленная команда и сколько секунд ещё ждать. */
    val pending: BeltCommand? = null,
    val pendingLeft: Int = 0,
) {
    /** Лента едет: дорожка сообщает ненулевую скорость. */
    val beltMoving: Boolean get() = speedKmh > 0.0

    /** Запускать есть смысл только стоящую ленту. */
    val canStart: Boolean get() = link == Link.READY && speedKnown && !beltMoving && pending == null

    /**
     * Выключать можно только едущую дорожку: если лента стоит, команда 08 01
     * сбивает прошивку (дорожка начинает вести себя непредсказуемо).
     */
    val canPowerOff: Boolean get() = link == Link.READY && speedKnown && beltMoving && pending == null

    /** Менять скорость — только на едущей ленте и не на паузе. */
    val canChangeSpeed: Boolean get() = link == Link.READY && speedKnown && beltMoving && !paused
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val ble = RookBle(app)
    private val userStore = UserStore(app)
    val users: StateFlow<List<User>> = userStore.users
    val favoriteId: StateFlow<String?> = userStore.favoriteId

    /** Кто сейчас тренируется. При запуске — избранный. */
    private val _currentId = MutableStateFlow(UserRules.initial(users.value, favoriteId.value)!!.id)
    val currentId: StateFlow<String> = _currentId
    private val currentUser: User get() = users.value.first { it.id == _currentId.value }

    private val session = Session(currentUser.profile.weightKg, currentUser.profile.strideM)
    private val recorder = WorkoutRecorder(currentUser.profile.strideM)
    private var lastSaveMs = 0L

    /** История у каждого пользователя своя, в отдельном файле. */
    private val stores = mutableMapOf<String, WorkoutStore>()
    private fun storeFor(userId: String) = stores.getOrPut(userId) { WorkoutStore(userStore.workoutsFile(userId)) }
    private val store: WorkoutStore get() = storeFor(_currentId.value)

    /** История тренировок пользователя для экрана статистики (включая идущую). */
    fun workoutsOf(userId: String): StateFlow<List<Workout>> = storeFor(userId).items

    private val _ui = MutableStateFlow(UiState(needsSetup = userStore.needsSetup))
    val ui: StateFlow<UiState> = _ui

    /** Целевая скорость в десятых км/ч, которую мы последний раз просили у дорожки. */
    private var targetTenths = Config.MIN_SPEED_TENTHS
    private var speedBeforePause = Config.MIN_SPEED_TENTHS

    init {
        viewModelScope.launch {
            ble.link.collect { l ->
                lockJob?.cancel()
                // Скорость от прошлого подключения устарела: ждём свежий пакет.
                _ui.value = _ui.value.copy(link = l, pending = null, pendingLeft = 0, speedKmh = 0.0, speedKnown = false)
            }
        }
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
                publish(d.speedKmh, fromTreadmill = true)
            }
        }
    }

    private fun record(now: Long) {
        val newWorkout = recorder.update(session.distanceM, session.seconds, session.kcal, now)
        recorder.current?.let(store::put)
        if (newWorkout) store.pruneShort(keepStartMs = recorder.current?.startMs)
        checkJourney()
        if (newWorkout || now - lastSaveMs >= Config.SAVE_EVERY_MS) {
            lastSaveMs = now
            viewModelScope.launch(Dispatchers.IO) { store.persist() }
        }
    }

    private val journeyPrefs = app.getSharedPreferences("journey", android.content.Context.MODE_PRIVATE)

    /**
     * Дошёл ли текущий пользователь до новой точки маршрута. Сколько точек уже показано —
     * помним по каждому пользователю. Первый раз (или после восстановления копии)
     * запоминаем молча, чтобы не завалить старыми цитатами.
     */
    private fun checkJourney() {
        val id = _currentId.value
        val totalKm = store.items.value.sumOf { it.distanceM } / 1000.0
        val reached = Journey.reached(totalKm)
        val key = "seen_$id"
        if (!journeyPrefs.contains(key)) {
            journeyPrefs.edit().putInt(key, reached.size).apply()
            return
        }
        val seen = journeyPrefs.getInt(key, 0)
        if (reached.size > seen) {
            journeyPrefs.edit().putInt(key, reached.size).apply()
            _ui.value = _ui.value.copy(milestones = _ui.value.milestones + reached.drop(seen))
        }
    }

    fun dismissMilestone() {
        _ui.value = _ui.value.copy(milestones = _ui.value.milestones.drop(1))
    }

    /** Итог последнего экспорта/импорта для экрана пользователей. */
    private val _backupStatus = MutableStateFlow<String?>(null)
    val backupStatus: StateFlow<String?> = _backupStatus

    fun exportBackup(uri: Uri) {
        saveStats()
        val data = BackupData(
            users = users.value,
            favoriteId = favoriteId.value,
            workouts = users.value.associate { it.id to storeFor(it.id).saved() },
        )
        val text = Backup.encode(data)
        val count = data.workouts.values.sumOf { it.size }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = try {
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } != null
            } catch (e: Exception) {
                false
            }
            _backupStatus.value = if (ok) "Сохранено: ${data.users.size} польз., $count трен." else "Не удалось сохранить файл"
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                try {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { String(it.readBytes()) }
                } catch (e: Exception) {
                    null
                }
            }
            val data = text?.let(Backup::decode)
            if (data == null) {
                _backupStatus.value = "Это не резервная копия Rook или файл повреждён"
                return@launch
            }
            restore(data)
            _backupStatus.value = "Восстановлено: ${data.users.size} польз., ${data.workouts.values.sumOf { it.size }} трен."
        }
    }

    private fun restore(data: BackupData) {
        finishWorkout()
        userStore.replaceAll(data.users, data.favoriteId)
        stores.keys.retainAll(data.users.map { it.id }.toSet())
        data.users.forEach { u -> storeFor(u.id).replace(data.workouts[u.id].orEmpty()) }
        journeyPrefs.edit().clear().apply()
        _currentId.value = UserRules.initial(users.value, favoriteId.value)!!.id
        applyProfile(currentUser.profile)
        _ui.value = _ui.value.copy(needsSetup = false, milestones = emptyList())
        publish()
    }

    /** Сохранить статистику на диск сразу (сворачивание, пауза, сброс). */
    fun saveStats() = store.persist()

    /** На экране — текущая тренировка. После простоя дольше 30 минут счётчики начинают с нуля. */
    private fun publish(speed: Double = _ui.value.speedKmh, fromTreadmill: Boolean = false) {
        val w = recorder.current
        _ui.value = _ui.value.copy(
            speedKmh = speed,
            speedKnown = _ui.value.speedKnown || fromTreadmill,
            distanceM = w?.distanceM ?: 0.0,
            seconds = w?.seconds?.toInt() ?: 0,
            steps = w?.steps ?: 0,
            kcal = w?.kcal ?: 0.0,
            paused = session.paused,
        )
    }

    /**
     * Сменить того, кто тренируется. Идущая тренировка сохраняется за прежним пользователем,
     * счётчики на экране обнуляются.
     */
    fun selectUser(userId: String) {
        if (userId == _currentId.value || users.value.none { it.id == userId }) return
        finishWorkout()
        _currentId.value = userId
        applyProfile(currentUser.profile)
        _ui.value = _ui.value.copy(milestones = emptyList())
        publish()
    }

    /** Создать или изменить пользователя. */
    fun saveUser(user: User) {
        userStore.save(user)
        if (user.id == _currentId.value) applyProfile(user.profile)
        _ui.value = _ui.value.copy(needsSetup = false)
        publish()
    }

    /** Удалить пользователя и его статистику. Последнего удалить нельзя. */
    fun deleteUser(userId: String) {
        if (users.value.size <= 1) return
        if (userId == _currentId.value) {
            val next = UserRules.initial(users.value.filterNot { it.id == userId }, favoriteId.value.takeIf { it != userId })
            next?.let { selectUser(it.id) }
        }
        if (userStore.delete(userId)) stores.remove(userId)
    }

    /** Избранный выбирается при запуске. Повторное нажатие снимает отметку. */
    fun toggleFavorite(userId: String) {
        userStore.setFavorite(if (favoriteId.value == userId) null else userId)
    }

    private fun applyProfile(p: Profile) {
        session.weightKg = p.weightKg
        session.strideM = p.strideM
        recorder.strideM = p.strideM
    }

    fun connect() = ble.connect()

    fun start() {
        if (!_ui.value.canStart) return
        session.paused = false
        ble.start()
        lock(BeltCommand.START)
        publish()
    }

    fun faster() = changeSpeed(+1)
    fun slower() = changeSpeed(-1)

    private fun changeSpeed(deltaTenths: Int) {
        if (!_ui.value.canChangeSpeed) return
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

    /** Закончить тренировку: сохранить и обнулить счётчики. Следующая начнётся с нуля. */
    private fun finishWorkout() {
        store.pruneShort(keepStartMs = null)
        saveStats()
        session.reset()
        recorder.reset()
        publish()
    }

    /** Полное выключение дорожки (включить обратно можно только кнопкой). */
    fun powerOff() {
        if (!_ui.value.canPowerOff) return
        ble.powerOff()
        lock(BeltCommand.POWER_OFF)
    }

    private var lockJob: Job? = null

    /** Дорожка отсчитывает 3 секунды: пока идёт отсчёт, повторная команда не уходит. */
    private fun lock(cmd: BeltCommand) {
        lockJob?.cancel()
        lockJob = viewModelScope.launch {
            for (left in Config.COMMAND_LOCK_S downTo 1) {
                _ui.value = _ui.value.copy(pending = cmd, pendingLeft = left)
                delay(1000)
            }
            _ui.value = _ui.value.copy(pending = null, pendingLeft = 0)
        }
    }

    fun dismissMessage() { _ui.value = _ui.value.copy(message = null) }

    override fun onCleared() {
        saveStats()
        ble.close()
    }
}
