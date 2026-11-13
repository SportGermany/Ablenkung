package de.sportgermany.handybremse

import android.app.usage.UsageStatsManager
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class Badge(val id: String, val emoji: String, val title: String, val text: String)

data class WeekCompare(val thisWeek: Int?, val lastWeek: Int?) {
    /** Positiv = so viele Minuten weniger als letzte Woche. */
    val saved: Int? get() = if (thisWeek != null && lastWeek != null) lastWeek - thisWeek else null
}

/** Statistik, Serie, Wochenvergleich, Abzeichen und die Bilanz für Claude. */
object Stats {

    val BADGES = listOf(
        Badge("goal1", "🎯", "Erster Zieltag", "Einen ganzen Tag unter deinem Tagesziel geblieben."),
        Badge("streak3", "🔥", "3 Tage am Stück", "3 Tage in Folge unter dem Tagesziel."),
        Badge("streak7", "🔥", "Eine ganze Woche", "7 Tage in Folge unter dem Tagesziel."),
        Badge("streak30", "🏆", "30-Tage-Serie", "30 Tage in Folge unter dem Tagesziel."),
        Badge("decl10", "🙌", "10× umentschieden", "10-mal „Doch nicht“ gesagt."),
        Badge("decl50", "💪", "50× umentschieden", "50-mal „Doch nicht“ gesagt."),
        Badge("decl100", "🦁", "100× umentschieden", "100-mal „Doch nicht“ gesagt."),
        Badge("night7", "🌙", "7 ruhige Nächte", "7 Nächte lang in der Nachtruhe keine gebremste App versucht."),
        Badge("focus1", "🧘", "Erster Fokus", "Einen Fokus-Timer bis zum Ende durchgezogen."),
        Badge("focus5", "🧠", "Fokus-Profi", "5 Fokus-Timer geschafft."),
        Badge("focus20", "🚀", "Fokus-Meister", "20 Fokus-Timer geschafft."),
        Badge("weekless", "📉", "Besser als letzte Woche", "In 7 Tagen weniger Zeit in Ablenkungs-Apps als in den 7 Tagen davor."),
    )

    fun badge(id: String) = BADGES.firstOrNull { it.id == id }

    private fun dayStart(offsetDays: Int): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -offsetDays)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Holt die Minuten der letzten 7 Tage vom Handy und merkt sie sich (langsam, nicht im UI-Thread aufrufen). */
    fun refreshMinutes(context: Context, store: Store) {
        if (!Apps.hasUsageAccess(context)) return
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val blocked = store.blocked
        for (off in 0..6) {
            val start = dayStart(off)
            val end = if (off == 0) System.currentTimeMillis() else dayStart(off - 1)
            val stats = try { usm.queryAndAggregateUsageStats(start, end) } catch (e: Exception) { continue }
            val ms = blocked.sumOf { stats[it]?.totalTimeInForeground ?: 0L }
            val day = store.dayKey(off)
            if (off == 0 || ms > 0 || store.minutes(day) < 0) store.setMinutes(day, (ms / 60_000L).toInt())
        }
    }

    private fun known(store: Store, day: String) = day >= store.firstDay()

    /** Abgeschlossene Tage in Folge (ab gestern rückwärts) unter dem Tagesziel. */
    fun streak(store: Store): Int {
        var n = 0
        for (off in 1..60) {
            val day = store.dayKey(off)
            if (!known(store, day)) break
            val m = store.minutes(day)
            if (m < 0 || m > store.dailyGoal) break
            n++
        }
        return n
    }

    private fun sumMinutes(store: Store, offsets: IntRange): Int? {
        var sum = 0
        for (off in offsets) {
            val day = store.dayKey(off)
            if (!known(store, day)) return null
            val m = store.minutes(day)
            if (m < 0) return null
            sum += m
        }
        return sum
    }

    /** Letzte 7 Tage (inkl. heute) gegen die 7 Tage davor. */
    fun week(store: Store) = WeekCompare(sumMinutes(store, 0..6), sumMinutes(store, 7..13))

    fun savedText(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        val dur = if (h > 0) "$h Std $m Min" else "$m Min"
        val training = minutes / 60
        val episodes = minutes / 25
        val extra = when {
            training >= 1 -> " – das sind $training Trainingseinheiten à 60 Minuten"
            episodes >= 1 -> " – das sind $episodes Serienfolgen à 25 Minuten"
            else -> ""
        }
        return "$dur$extra"
    }

    fun fmtMin(m: Int) = if (m >= 60) "${m / 60} Std ${m % 60} Min" else "$m Min"

    /** Prüft alle Abzeichen und vergibt neue. */
    fun checkBadges(store: Store) {
        fun grant(id: String, ok: Boolean) { if (ok && store.badgeDate(id) == null) store.grantBadge(id) }
        val s = streak(store)
        grant("goal1", s >= 1)
        grant("streak3", s >= 3)
        grant("streak7", s >= 7)
        grant("streak30", s >= 30)
        val d = store.declinedTotal()
        grant("decl10", d >= 10)
        grant("decl50", d >= 50)
        grant("decl100", d >= 100)
        val nightOk = store.nightOn && (1..7).all { off -> known(store, store.dayKey(off)) && store.nightTries(store.dayKey(off)) == 0 }
        grant("night7", nightOk)
        val f = store.focusDone()
        grant("focus1", f >= 1)
        grant("focus5", f >= 5)
        grant("focus20", f >= 20)
        grant("weekless", (week(store).saved ?: 0) > 0)
    }

    fun isSunday() = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY

    /** Text zum Kopieren, den man Claude schicken kann. */
    fun summaryText(context: Context, store: Store): String {
        val df = SimpleDateFormat("EE dd.MM.", Locale.GERMANY)
        val sb = StringBuilder()
        sb.appendLine("Meine Handybremse-Bilanz (letzte 7 Tage)")
        sb.appendLine("Tagesziel: ${store.dailyGoal} Min · Limit: ${store.dailyLimit}× pro App · Nachtruhe: " +
            if (store.nightOn) "${hhmm(store.nightFrom)}–${hhmm(store.nightTo)}" else "aus")
        sb.appendLine()
        for (off in 6 downTo 0) {
            val day = store.dayKey(off)
            if (!known(store, day)) continue
            val date = df.format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -off) }.time)
            val m = store.minutes(day)
            val opens = store.opensByApp(day).filterValues { it > 0 }
                .map { (pkg, n) -> "${Apps.label(context, pkg)} ${n}×" }.joinToString(", ").ifEmpty { "nichts geöffnet" }
            val extra = buildList {
                if (store.declined(day) > 0) add("${store.declined(day)}× umentschieden")
                if (store.nightTries(day) > 0) add("${store.nightTries(day)}× in der Nachtruhe versucht")
            }.joinToString(", ")
            sb.appendLine("$date: ${if (m >= 0) fmtMin(m) else "? Min"} · $opens" + if (extra.isNotEmpty()) " · $extra" else "")
        }
        sb.appendLine()
        val w = week(store)
        w.saved?.let { sb.appendLine(if (it >= 0) "Im Vergleich zur Vorwoche: ${fmtMin(it)} weniger" else "Im Vergleich zur Vorwoche: ${fmtMin(-it)} mehr") }
        sb.appendLine("Serie unter dem Tagesziel: ${streak(store)} Tage")
        sb.appendLine("Fokus-Timer geschafft: ${store.focusDone()} · Umentschieden insgesamt: ${store.declinedTotal()}")
        sb.appendLine()
        sb.append("Claude, wie findest du meine Woche? Was kann ich besser machen?")
        return sb.toString()
    }
}
