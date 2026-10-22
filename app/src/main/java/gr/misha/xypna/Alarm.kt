package gr.misha.xypna

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

const val EXTRA_ID = "alarm_id"

/** days: bitmask, bit 0 = Δευτέρα ... bit 6 = Κυριακή. 0 = μία φορά. */
data class Alarm(
    var id: Int,
    var hour: Int,
    var minute: Int,
    var days: Int = 0,
    var enabled: Boolean = true,
    var label: String = "",
    var difficulty: Int = 1,
    var problems: Int = 3
) {
    fun timeText() = "%02d:%02d".format(hour, minute)

    fun daysText(): String = when (days) {
        0 -> "Μία φορά"
        0b1111111 -> "Κάθε μέρα"
        0b0011111 -> "Καθημερινές"
        0b1100000 -> "Σαββατοκύριακο"
        else -> DAY_SHORT.indices
            .filter { (days and (1 shl it)) != 0 }
            .joinToString(" ") { DAY_SHORT[it] }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("hour", hour); put("minute", minute); put("days", days)
        put("enabled", enabled); put("label", label)
        put("difficulty", difficulty); put("problems", problems)
    }

    companion object {
        val DAY_SHORT = arrayOf("Δευ", "Τρι", "Τετ", "Πεμ", "Παρ", "Σαβ", "Κυρ")
        val DAY_LETTER = arrayOf("Δ", "Τ", "Τ", "Π", "Π", "Σ", "Κ")
        val DIFFICULTY = arrayOf("Εύκολο", "Μέτριο", "Δύσκολο")
        val EXAMPLE = arrayOf("π.χ. 43 + 27", "π.χ. 7 × 18 + 56", "π.χ. 14 × 37 + 512")

        fun fromJson(o: JSONObject) = Alarm(
            id = o.getInt("id"),
            hour = o.getInt("hour"),
            minute = o.getInt("minute"),
            days = o.optInt("days", 0),
            enabled = o.optBoolean("enabled", true),
            label = o.optString("label", ""),
            difficulty = o.optInt("difficulty", 1),
            problems = o.optInt("problems", 3)
        )
    }
}

object AlarmStore {
    private const val PREFS = "alarms"
    private const val KEY = "list"

    fun all(ctx: Context): MutableList<Alarm> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (e: Exception) { JSONArray() }
        val list = MutableList(arr.length()) { Alarm.fromJson(arr.getJSONObject(it)) }
        list.sortWith(compareBy({ it.hour }, { it.minute }))
        return list
    }

    private fun saveAll(ctx: Context, list: List<Alarm>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).commit()
    }

    fun get(ctx: Context, id: Int): Alarm? = all(ctx).firstOrNull { it.id == id }

    fun upsert(ctx: Context, a: Alarm) {
        val list = all(ctx)
        val i = list.indexOfFirst { it.id == a.id }
        if (i >= 0) list[i] = a else list.add(a)
        saveAll(ctx, list)
    }

    fun delete(ctx: Context, id: Int) {
        saveAll(ctx, all(ctx).filter { it.id != id })
    }

    fun nextId(ctx: Context): Int = (all(ctx).maxOfOrNull { it.id } ?: 0) + 1
}
