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

    // ---------- Weitere Einstellungen ----------

    /** Apps, bei denen nur Reels/Shorts gebremst werden (nicht die ganze App). */
    var shortsOnly: Set<String>
        get() = p.getStringSet("shortsOnly", null) ?: Shorts.SUPPORTED.keys
        set(v) = p.edit().putStringSet("shortsOnly", v.toSet()).apply()

    fun isShortsOnly(pkg: String) = pkg in Shorts.SUPPORTED && pkg in shortsOnly

    /** Tagesziel: höchstens so viele Minuten in gebremsten Apps. */
    var dailyGoal: Int
        get() = p.getInt("dailyGoal", 45)
        set(v) = p.edit().putInt("dailyGoal", v.coerceIn(5, 300)).apply()

    /** Apps, die als Alternative vorgeschlagen werden. */
    var altApps: List<String>
        get() = p.getString("altApps", "")!!.split(",").filter { it.isNotBlank() }
        set(v) = p.edit().putString("altApps", v.joinToString(",")).apply()

    // ---------- Wochenende ----------

    var weekendDifferent: Boolean
        get() = p.getBoolean("weekendDifferent", false)
        set(v) = p.edit().putBoolean("weekendDifferent", v).apply()

    var weekendLimit: Int
        get() = p.getInt("weekendLimit", dailyLimit)
        set(v) = p.edit().putInt("weekendLimit", v.coerceIn(1, 30)).apply()

    var weekendGoal: Int
        get() = p.getInt("weekendGoal", dailyGoal)
        set(v) = p.edit().putInt("weekendGoal", v.coerceIn(5, 300)).apply()

    private fun calOf(day: String): Calendar = Calendar.getInstance().apply {
        time = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).parse(day) ?: Date()
    }

    fun isWeekend(day: String = today()): Boolean {
        val d = calOf(day).get(Calendar.DAY_OF_WEEK)
        return d == Calendar.SATURDAY || d == Calendar.SUNDAY
    }

    /** Öffnungen pro App an diesem Tag (Wochenende kann anders sein). */
    fun limitFor(day: String = today()) = if (weekendDifferent && isWeekend(day)) weekendLimit else dailyLimit

    /** Tagesziel in Minuten an diesem Tag. */
    fun goalFor(day: String = today()) = if (weekendDifferent && isWeekend(day)) weekendGoal else dailyGoal

    // ---------- Sperrzeiten (z. B. Schule, Training) ----------

    /** days: 7 Zeichen Mo..So, "1" = an diesem Tag aktiv. from/to: Minuten seit Mitternacht. */
    data class Block(val name: String, val days: String, val from: Int, val to: Int)

    var blocks: List<Block>
        get() = p.getString("blocks", "")!!.split("\n").filter { it.isNotBlank() }.mapNotNull { line ->
            val f = line.split("|")
            if (f.size == 4) Block(f[0], f[1], f[2].toIntOrNull() ?: 0, f[3].toIntOrNull() ?: 0) else null
        }
        set(v) = p.edit().putString("blocks", v.joinToString("\n") {
            "${it.name.replace("|", " ").replace("\n", " ")}|${it.days}|${it.from}|${it.to}"
        }).apply()

    /** Die gerade laufende Sperrzeit oder null. */
    fun activeBlock(now: Long = System.currentTimeMillis()): Block? {
        val c = Calendar.getInstance().apply { timeInMillis = now }
        val idx = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7   // Mo=0 … So=6
        val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        return blocks.firstOrNull { b -> b.days.getOrNull(idx) == '1' && b.from < b.to && m >= b.from && m < b.to }
    }

    /** Nachtruhe, Fokus oder Sperrzeit: alle gebremsten Apps komplett zu. */
    fun hardLocked(now: Long = System.currentTimeMillis()) = isNight(now) || focusActive(now) || activeBlock(now) != null

    // ---------- Zeit pro App ----------

    /** Minuten pro Tag für diese App, 0 = kein eigenes Limit. */
    fun appLimit(pkg: String) = p.getInt("appLimit_$pkg", 0)
    fun setAppLimit(pkg: String, minutes: Int) = p.edit().putInt("appLimit_$pkg", minutes.coerceIn(0, 300)).apply()

    // ---------- Fokus-Wald ----------

    /** Bäume: Minuten eines geschafften Fokus oder "x" für einen abgebrochenen. */
    fun forest(): List<String> = p.getString("forest", "")!!.split(",").filter { it.isNotBlank() }
    private fun plant(tree: String) = p.edit().putString("forest", (forest() + tree).takeLast(500).joinToString(",")).apply()
    fun focusMinutes() = p.getInt("focusMinutes", 25)

    // ---------- Tageszähler (pro Datum gespeichert, für die Statistik) ----------

    fun dayKey(offsetDays: Int = 0): String {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, -offsetDays)
        return SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(c.time)
    }

    private fun today() = dayKey(0)

    private fun ensureDay() {
        val t = today()
        if (p.getString("day", null) == t) return
        // Alte Einträge (älter als 60 Tage) aufräumen
        val keep = (0..60).map { dayKey(it) }.toSet()
        val e = p.edit()
        p.all.keys.filter { k ->
            val m = Regex("^[odmnx]_(\\d{4}-\\d{2}-\\d{2})").find(k) ?: return@filter k.startsWith("opens_")
            m.groupValues[1] !in keep
        }.forEach { e.remove(it) }
        e.putString("day", t).apply()
    }

    fun opens(pkg: String, day: String = today()): Int { ensureDay(); return p.getInt("o_${day}_$pkg", 0) }
    fun opensToday(pkg: String) = opens(pkg)
    fun opensAll(day: String): Int = p.all.entries.filter { it.key.startsWith("o_${day}_") }.sumOf { (it.value as? Int) ?: 0 }
    fun opensByApp(day: String): Map<String, Int> =
        p.all.entries.filter { it.key.startsWith("o_${day}_") }.associate { it.key.removePrefix("o_${day}_") to ((it.value as? Int) ?: 0) }

    fun declined(day: String = today()): Int { ensureDay(); return p.getInt("d_$day", 0) }
    fun declinedToday() = declined()
    fun declinedTotal() = p.getInt("declinedTotal", 0)
    fun addDeclined() {
        ensureDay()
        p.edit().putInt("d_${today()}", declined() + 1).putInt("declinedTotal", declinedTotal() + 1).apply()
    }

    /** Minuten in gebremsten Apps (vom Handy gemessen und hier gemerkt). -1 = unbekannt. */
    fun minutes(day: String): Int = p.getInt("m_$day", -1)
    fun setMinutes(day: String, m: Int) = p.edit().putInt("m_$day", m).apply()

    fun nightTries(day: String): Int = p.getInt("n_$day", 0)
    fun addNightTry() { ensureDay(); p.edit().putInt("n_${today()}", nightTries(today()) + 1).apply() }

    fun limitReached(pkg: String) = opensToday(pkg) >= limitFor()

    /** Tag, ab dem es Daten gibt (erster Start). */
    fun firstDay(): String {
        p.getString("firstDay", null)?.let { return it }
        val t = today(); p.edit().putString("firstDay", t).apply(); return t
    }

    // ---------- Sitzungen (freigegebene Zeit in einer App) ----------

    fun sessionEnd(pkg: String): Long = p.getLong("session_$pkg", 0L)

    fun sessionActive(pkg: String, now: Long = System.currentTimeMillis()): Boolean =
        sessionEnd(pkg) > now && !hardLocked(now)

    fun intention(pkg: String): String? = p.getString("intention_$pkg", null)

    fun startSession(pkg: String, minutes: Int, intention: String) {
        ensureDay()
        p.edit()
            .putLong("session_$pkg", System.currentTimeMillis() + minutes * 60_000L)
            .putString("intention_$pkg", intention)
            .putInt("o_${today()}_$pkg", opensToday(pkg) + 1)
            .putBoolean("extended_$pkg", false)
            .apply()
    }

    fun canExtend(pkg: String) = !p.getBoolean("extended_$pkg", false) && !limitReached(pkg)

    /** Einmal um 5 Minuten verlängern – zählt als eine Öffnung. */
    fun extend(pkg: String, minutes: Int = 5) {
        ensureDay()
        p.edit()
            .putLong("session_$pkg", System.currentTimeMillis() + minutes * 60_000L)
            .putInt("o_${today()}_$pkg", opensToday(pkg) + 1)
            .putBoolean("extended_$pkg", true)
            .apply()
    }

    fun endSession(pkg: String) {
        p.edit().putLong("session_$pkg", 0L).apply()
    }

    // ---------- Fokus-Timer ----------

    fun focusUntil() = p.getLong("focusUntil", 0)
    fun focusActive(now: Long = System.currentTimeMillis()) = focusUntil() > now
    fun startFocus(minutes: Int) = p.edit().putLong("focusUntil", System.currentTimeMillis() + minutes * 60_000L)
        .putInt("focusMinutes", minutes).putBoolean("focusCounted", false).apply()
    fun cancelFocus() {
        if (focusActive()) plant("x")
        p.edit().putLong("focusUntil", 0).putBoolean("focusCounted", true).apply()
    }
    fun focusDone() = p.getInt("focusDone", 0)

    /** Zählt einen abgeschlossenen Fokus genau einmal. Gibt true zurück, wenn gerade einer fertig geworden ist. */
    fun collectFinishedFocus(): Boolean {
        if (focusUntil() == 0L || focusActive() || p.getBoolean("focusCounted", true)) return false
        p.edit().putBoolean("focusCounted", true).putInt("focusDone", focusDone() + 1).apply()
        plant(focusMinutes().toString())
        return true
    }

    // ---------- Abzeichen ----------

    fun badgeDate(id: String): String? = p.getString("badge_$id", null)
    fun grantBadge(id: String) = p.edit().putString("badge_$id", today()).putString("newBadge", id).apply()
    fun takeNewBadge(): String? = p.getString("newBadge", null)
    fun clearNewBadge() = p.edit().remove("newBadge").apply()

    // ---------- Wochen-Rückblick ----------

    fun reviewDismissed(day: String) = p.getBoolean("x_${day}_review", false)
    fun dismissReview(day: String) = p.edit().putBoolean("x_${day}_review", true).apply()

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
