package de.sportgermany.handybremse

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Sicherung: alle Einstellungen und die Statistik als JSON-Datei. */
object Backup {
    private const val PREFS = "handybremse"

    /** Diese Werte werden nie gesichert/wiederhergestellt (Sicherheit und laufende Zustände). */
    private fun skip(key: String) = key in setOf(
        "settingsUntil", "uninstallUntil", "settingsSticky", "guardStarted", "focusUntil", "focusCounted", "newBadge",
    ) || key.startsWith("challenge_") || key.startsWith("session_") || key.startsWith("extended_")

    fun export(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val data = JSONObject()
        for ((k, v) in prefs.all) {
            if (skip(k) || v == null) continue
            val entry = JSONObject()
            when (v) {
                is Int -> entry.put("t", "i").put("v", v)
                is Long -> entry.put("t", "l").put("v", v)
                is Boolean -> entry.put("t", "b").put("v", v)
                is Float -> entry.put("t", "f").put("v", v.toDouble())
                is String -> entry.put("t", "s").put("v", v)
                is Set<*> -> entry.put("t", "set").put("v", JSONArray(v.map { it.toString() }))
                else -> continue
            }
            data.put(k, entry)
        }
        return JSONObject()
            .put("app", "Handybremse")
            .put("version", BuildConfig.VERSION_NAME)
            .put("created", System.currentTimeMillis())
            .put("data", data)
            .toString(2)
    }

    /** Stellt eine Sicherung wieder her. Gibt die Anzahl der Werte zurück oder wirft eine Exception. */
    fun import(context: Context, json: String): Int {
        val root = JSONObject(json)
        require(root.optString("app") == "Handybremse") { "Das ist keine Handybremse-Sicherung." }
        val data = root.getJSONObject("data")
        val e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        var n = 0
        for (k in data.keys()) {
            if (skip(k)) continue
            val entry = data.getJSONObject(k)
            when (entry.getString("t")) {
                "i" -> e.putInt(k, entry.getInt("v"))
                "l" -> e.putLong(k, entry.getLong("v"))
                "b" -> e.putBoolean(k, entry.getBoolean("v"))
                "f" -> e.putFloat(k, entry.getDouble("v").toFloat())
                "s" -> e.putString(k, entry.getString("v"))
                "set" -> {
                    val arr = entry.getJSONArray("v")
                    e.putStringSet(k, (0 until arr.length()).map { arr.getString(it) }.toSet())
                }
                else -> continue
            }
            n++
        }
        e.commit()
        return n
    }

    /** Aufräumen ohne Einstellungen zu verlieren: hängende Zustände lösen, Zwischenspeicher leeren. */
    fun repair(context: Context): List<String> {
        val done = mutableListOf<String>()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val e = prefs.edit()
        val now = System.currentTimeMillis()
        // abgelaufene Sitzungen entfernen
        prefs.all.filter { it.key.startsWith("session_") && ((it.value as? Long) ?: 0L) < now }.keys.forEach { e.remove(it) }
        e.remove("newBadge")
        // kaputte Einträge entfernen (falscher Typ)
        listOf("dailyLimit", "dailyGoal", "weekendLimit", "weekendGoal", "nightFrom", "nightTo").forEach { k ->
            if (prefs.all[k] != null && prefs.all[k] !is Int) e.remove(k)
        }
        e.commit()
        done += "Abgelaufene Sitzungen und Hinweise aufgeräumt"
        try {
            context.cacheDir.deleteRecursively()
            done += "Zwischenspeicher geleert"
        } catch (_: Exception) {}
        Apps.all(context, refresh = true)
        done += "App-Liste neu eingelesen"
        return done
    }
}
