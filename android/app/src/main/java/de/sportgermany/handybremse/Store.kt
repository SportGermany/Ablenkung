package de.sportgermany.handybremse

import android.content.Context
import android.content.SharedPreferences
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Alle gespeicherten Daten der Handybremse (nur lokal auf dem Handy). */
class Store(context: Context) {
    private val p: SharedPreferences =
        context.applicationContext.getSharedPreferences("handybremse", Context.MODE_PRIVATE)

    companion object {
        val DEFAULT_BLOCKED = setOf(
            "com.zhiliaoapp.musically",   // TikTok
            "com.ss.android.ugc.trill",   // TikTok (andere Region)
            "com.zhiliaoapp.musically.go", // TikTok Lite
            "com.instagram.android",      // Instagram
            "com.google.android.youtube", // YouTube
        )
        const val SETTINGS_MINUTES = 10
        const val UNINSTALL_MINUTES = 15
        const val MAX_FAVORITES = 8
    }

    // ---------- Einstellungen ----------

    var blocked: Set<String>
        get() = p.getStringSet("blocked", null) ?: DEFAULT_BLOCKED
        set(v) = p.edit().putStringSet("blocked", v.toSet()).apply()

    /** Reihenfolge zählt, darum als Text gespeichert. null = noch nicht eingerichtet. */
    var favorites: List<String>?
        get() = p.getString("favorites", null)?.split(",")?.filter { it.isNotBlank() }
        set(v) = p.edit().putString("favorites", v?.joinToString(",")).apply()

    /** Wie oft pro Tag jede gebremste App geöffnet werden darf. */
    var dailyLimit: Int
        get() = p.getInt("dailyLimit", 5)
        set(v) = p.edit().putInt("dailyLimit", v.coerceIn(1, 30)).apply()

    var nightOn: Boolean
        get() = p.getBoolean("nightOn", true)
        set(v) = p.edit().putBoolean("nightOn", v).apply()

    /** Minuten seit Mitternacht. */
    var nightFrom: Int
        get() = p.getInt("nightFrom", 22 * 60)
        set(v) = p.edit().putInt("nightFrom", Math.floorMod(v, 24 * 60)).apply()

    var nightTo: Int
        get() = p.getInt("nightTo", 7 * 60)
        set(v) = p.edit().putInt("nightTo", Math.floorMod(v, 24 * 60)).apply()

    fun isNight(now: Long = System.currentTimeMillis()): Boolean {
        if (!nightOn) return false
        val c = Calendar.getInstance().apply { timeInMillis = now }
        val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        val from = nightFrom
        val to = nightTo
        return if (from == to) false else if (from < to) m in from until to else (m >= from || m < to)
    }

    // ---------- Tageszähler ----------

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(Date())

    private fun ensureDay() {
        val t = today()
        if (p.getString("day", null) != t) {
            val e = p.edit()
            p.all.keys.filter { it.startsWith("opens_") }.forEach { e.remove(it) }
            e.putInt("declined", 0)
            e.putString("day", t)
            e.apply()
        }
    }

    fun opensToday(pkg: String): Int { ensureDay(); return p.getInt("opens_$pkg", 0) }
    fun declinedToday(): Int { ensureDay(); return p.getInt("declined", 0) }
    fun addDeclined() { ensureDay(); p.edit().putInt("declined", declinedToday() + 1).apply() }

    fun limitReached(pkg: String) = opensToday(pkg) >= dailyLimit

    // ---------- Sitzungen (freigegebene Zeit in einer App) ----------

    fun sessionEnd(pkg: String): Long = p.getLong("session_$pkg", 0L)

    fun sessionActive(pkg: String, now: Long = System.currentTimeMillis()): Boolean =
        sessionEnd(pkg) > now && !isNight(now)

    fun intention(pkg: String): String? = p.getString("intention_$pkg", null)

    fun startSession(pkg: String, minutes: Int, intention: String) {
        ensureDay()
        p.edit()
            .putLong("session_$pkg", System.currentTimeMillis() + minutes * 60_000L)
            .putString("intention_$pkg", intention)
            .putInt("opens_$pkg", opensToday(pkg) + 1)
            .apply()
    }

    fun endSession(pkg: String) {
        p.edit().putLong("session_$pkg", 0L).apply()
    }

    // ---------- Freischaltungen ----------

    fun settingsUnlocked(now: Long = System.currentTimeMillis()) = p.getLong("settingsUntil", 0) > now
    fun settingsUntil() = p.getLong("settingsUntil", 0)
    /**
     * sticky = true: offen für [minutes], auch wenn man zwischendurch auf den Startbildschirm geht.
     * sticky = false: offen nur, solange man in den Einstellungen bleibt (beim Verlassen wird gesperrt).
     */
    fun unlockSettings(minutes: Int = SETTINGS_MINUTES, sticky: Boolean = true) =
        p.edit().putLong("settingsUntil", System.currentTimeMillis() + minutes * 60_000L)
            .putBoolean("settingsSticky", sticky).apply()

    fun settingsSticky() = p.getBoolean("settingsSticky", true)

    fun lockSettings() = p.edit().putLong("settingsUntil", 0).apply()

    fun uninstallUnlocked(now: Long = System.currentTimeMillis()) = p.getLong("uninstallUntil", 0) > now
    fun unlockUninstall(minutes: Int = UNINSTALL_MINUTES) =
        p.edit().putLong("uninstallUntil", System.currentTimeMillis() + minutes * 60_000L).apply()

    /** true nur beim allerersten Start des Wächters. */
    fun firstGuardStart(): Boolean {
        if (p.getBoolean("guardStarted", false)) return false
        p.edit().putBoolean("guardStarted", true).apply()
        return true
    }

    fun lockAll() = p.edit().putLong("settingsUntil", 0).putLong("uninstallUntil", 0).apply()

    /** Anfrage-Nummer für eine Aktion ("E" = Einstellungen, "D" = Deinstallation). Bleibt gleich, bis sie benutzt wurde. */
    fun challenge(action: Char): String {
        val key = "challenge_$action"
        p.getString(key, null)?.let { return it }
        val c = "%06d".format(SecureRandom().nextInt(1_000_000))
        p.edit().putString(key, c).apply()
        return c
    }

    fun renewChallenge(action: Char) = p.edit().remove("challenge_$action").apply()

}
