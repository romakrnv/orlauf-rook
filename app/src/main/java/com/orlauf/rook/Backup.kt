package com.orlauf.rook

import org.json.JSONException
import org.json.JSONObject

/** Содержимое резервной копии: все пользователи, избранный и история каждого. */
data class BackupData(
    val users: List<User>,
    val favoriteId: String?,
    val workouts: Map<String, List<Workout>>,
)

/**
 * Резервная копия — один JSON-файл. История каждого пользователя внутри — в том же
 * текстовом формате, что и на диске (WorkoutCodec), чтобы не держать два формата.
 */
object Backup {
    private const val VERSION = 1

    fun encode(data: BackupData): String = JSONObject().apply {
        put("app", "rook")
        put("version", VERSION)
        put("favoriteId", data.favoriteId ?: JSONObject.NULL)
        put("users", UserJson.encode(data.users))
        put("workouts", JSONObject().apply {
            data.workouts.forEach { (id, ws) -> put(id, WorkoutCodec.encode(ws)) }
        })
    }.toString(2)

    /** null — файл не наш или повреждён. */
    fun decode(json: String): BackupData? = try {
        val o = JSONObject(json)
        if (o.optString("app") != "rook" || o.optInt("version") > VERSION) {
            null
        } else {
            val users = UserJson.decode(o.getJSONArray("users"))
            val ws = o.optJSONObject("workouts")
            BackupData(
                users = users,
                favoriteId = if (o.isNull("favoriteId")) null else o.optString("favoriteId"),
                workouts = users.associate { u -> u.id to WorkoutCodec.decode(ws?.optString(u.id).orEmpty()) },
            ).takeIf { users.isNotEmpty() }
        }
    } catch (e: JSONException) {
        null
    }
}
