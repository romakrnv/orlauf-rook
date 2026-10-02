package com.orlauf.rook

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Пользователь дорожки: аватар (эмодзи на цветном кружке) и параметры для расчёта калорий и шагов. */
data class User(
    val id: String,
    val name: String,
    val emoji: String,
    /** ARGB цвета кружка, из [Avatars.COLORS]. */
    val color: Long,
    val profile: Profile,
)

object Avatars {
    val EMOJI = listOf(
        "🏃", "🚶", "🐻", "🦊", "🐱", "🐶", "🐼", "🐨",
        "🐯", "🦁", "🐸", "🐵", "🦄", "🐧", "🐢", "🦉",
    )
    val COLORS = listOf(
        0xFF5C6BC0, 0xFF26A69A, 0xFFEF5350, 0xFFFFA726,
        0xFFAB47BC, 0xFF66BB6A, 0xFF42A5F5, 0xFF8D6E63,
    )
}

/** Правила выбора пользователя, без Android — чтобы покрыть тестами. */
object UserRules {
    /** При запуске: избранный, иначе первый. */
    fun initial(users: List<User>, favoriteId: String?): User? =
        users.firstOrNull { it.id == favoriteId } ?: users.firstOrNull()

    /** Новый пользователь получает аватар, которого ещё ни у кого нет (если такой остался). */
    fun nextAvatar(users: List<User>): Pair<String, Long> {
        val emoji = Avatars.EMOJI.firstOrNull { e -> users.none { it.emoji == e } } ?: Avatars.EMOJI.first()
        val color = Avatars.COLORS.firstOrNull { c -> users.none { it.color == c } } ?: Avatars.COLORS.first()
        return emoji to color
    }

    fun newUser(users: List<User>, name: String = "Пользователь ${users.size + 1}"): User {
        val (emoji, color) = nextAvatar(users)
        return User(UUID.randomUUID().toString(), name, emoji, color, Profile())
    }
}

/**
 * Список пользователей и избранный — в SharedPreferences, статистика — в отдельном файле на каждого.
 * Последнего пользователя удалить нельзя: приложению всегда есть за кем записывать тренировку.
 */
class UserStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("users", Context.MODE_PRIVATE)

    private val _users = MutableStateFlow(loadOrCreate())
    val users: StateFlow<List<User>> = _users

    private val _favoriteId = MutableStateFlow(prefs.getString(KEY_FAVORITE, null))
    val favoriteId: StateFlow<String?> = _favoriteId

    /** Первый пользователь создан автоматически и ещё не настроен (имя, вес, рост). */
    val needsSetup: Boolean get() = !prefs.getBoolean(KEY_SETUP_DONE, false)

    fun workoutsFile(userId: String) = File(context.filesDir, "workouts_$userId.csv")

    fun save(user: User) {
        val list = _users.value
        val i = list.indexOfFirst { it.id == user.id }
        _users.value = if (i >= 0) list.toMutableList().also { it[i] = user } else list + user
        prefs.edit().putString(KEY_LIST, UserJson.encode(_users.value).toString()).putBoolean(KEY_SETUP_DONE, true).apply()
    }

    /** Удаляет пользователя вместе с его статистикой. Возвращает false для последнего. */
    fun delete(userId: String): Boolean {
        if (_users.value.size <= 1) return false
        _users.value = _users.value.filterNot { it.id == userId }
        if (_favoriteId.value == userId) setFavorite(null)
        prefs.edit().putString(KEY_LIST, UserJson.encode(_users.value).toString()).apply()
        workoutsFile(userId).delete()
        return true
    }

    /** Заменить всех пользователей (восстановление из резервной копии). Статистику пишет вызывающий. */
    fun replaceAll(newUsers: List<User>, favoriteId: String?) {
        require(newUsers.isNotEmpty())
        val keep = newUsers.map { it.id }.toSet()
        _users.value.filter { it.id !in keep }.forEach { workoutsFile(it.id).delete() }
        _users.value = newUsers
        _favoriteId.value = favoriteId?.takeIf { it in keep }
        prefs.edit()
            .putString(KEY_LIST, UserJson.encode(newUsers).toString())
            .putString(KEY_FAVORITE, _favoriteId.value)
            .putBoolean(KEY_SETUP_DONE, true)
            .apply()
    }

    fun setFavorite(userId: String?) {
        _favoriteId.value = userId
        prefs.edit().putString(KEY_FAVORITE, userId).apply()
    }

    private fun loadOrCreate(): List<User> {
        prefs.getString(KEY_LIST, null)?.let { json -> UserJson.decodeOrEmpty(json).takeIf { it.isNotEmpty() }?.let { return it } }
        return listOf(migrateFirstUser())
    }

    /**
     * Первый запуск этой версии: создаём одного пользователя. Если до этого уже были вес/рост
     * и история (версия без пользователей), переносим их ему.
     */
    private fun migrateFirstUser(): User {
        val old = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
        val hadProfile = old.contains("weight_kg")
        val profile = if (hadProfile) Profile(
            weightKg = old.getFloat("weight_kg", Profile.DEFAULT_WEIGHT_KG.toFloat()).toDouble(),
            heightCm = old.getFloat("height_cm", Profile.DEFAULT_HEIGHT_CM.toFloat()).toDouble(),
            strideCm = if (old.contains("stride_cm")) old.getFloat("stride_cm", 0f).toDouble() else null,
        ) else Profile()

        val user = UserRules.newUser(emptyList(), name = "Я").copy(profile = profile)
        File(context.filesDir, "workouts.csv").takeIf { it.exists() }?.renameTo(workoutsFile(user.id))

        prefs.edit()
            .putString(KEY_LIST, UserJson.encode(listOf(user)).toString())
            .putString(KEY_FAVORITE, user.id)
            .putBoolean(KEY_SETUP_DONE, hadProfile)
            .apply()
        old.edit().clear().apply()
        return user
    }

    private companion object {
        const val KEY_LIST = "list"
        const val KEY_FAVORITE = "favorite"
        const val KEY_SETUP_DONE = "setup_done"
    }
}

/** Пользователи в JSON: для SharedPreferences и для резервной копии. */
object UserJson {
    fun encode(users: List<User>): JSONArray = JSONArray().apply {
        users.forEach { u ->
            put(JSONObject().apply {
                put("id", u.id)
                put("name", u.name)
                put("emoji", u.emoji)
                put("color", u.color)
                put("weightKg", u.profile.weightKg)
                put("heightCm", u.profile.heightCm)
                u.profile.strideCm?.let { put("strideCm", it) }
            })
        }
    }

    /** Бросает JSONException на битых данных. */
    fun decode(arr: JSONArray): List<User> = (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        User(
            id = o.getString("id"),
            name = o.getString("name"),
            emoji = o.getString("emoji"),
            color = o.getLong("color"),
            profile = Profile(
                weightKg = o.getDouble("weightKg"),
                heightCm = o.getDouble("heightCm"),
                strideCm = if (o.has("strideCm")) o.getDouble("strideCm") else null,
            ),
        )
    }

    fun decodeOrEmpty(json: String): List<User> = try {
        decode(JSONArray(json))
    } catch (e: org.json.JSONException) {
        emptyList()
    }
}
