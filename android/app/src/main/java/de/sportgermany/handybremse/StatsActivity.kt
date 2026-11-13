package de.sportgermany.handybremse

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Statistik der letzten 7 Tage, Wochenvergleich, Serie und Abzeichen. Braucht keinen Code. */
class StatsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HbTheme { StatsScreen() } }
    }

    private data class Day(val label: String, val minutes: Int, val opens: Int, val declined: Int, val known: Boolean)

    @Composable
    private fun StatsScreen() {
        val store = remember { Store(this) }
        val loaded by produceState(initialValue = false) {
            withContext(Dispatchers.Default) {
                Stats.refreshMinutes(this@StatsActivity, store)
                Stats.checkBadges(store)
            }
            value = true
        }
        @Suppress("UNUSED_EXPRESSION") loaded
        val hasUsage = Apps.hasUsageAccess(this)
        val df = SimpleDateFormat("EE", Locale.GERMANY)
        val days = (6 downTo 0).map { off ->
            val key = store.dayKey(off)
            val date = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -off) }.time
            Day(if (off == 0) "Heute" else df.format(date).take(2), store.minutes(key), store.opensAll(key),
                store.declined(key), key >= store.firstDay())
        }
        val week = Stats.week(store)
        val streak = Stats.streak(store)
        val goal = store.goalFor()

        Column(
            Modifier.fillMaxSize().background(C.bg).safeDrawingPadding()
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Title("📊 Statistik")

            // Heute
            Card {
                Text("Heute", color = C.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                val today = days.last()
                if (hasUsage && today.minutes >= 0) GoalBar(today.minutes, goal)
                Body("${today.opens}× gebremste Apps geöffnet · ${today.declined}× umentschieden")
                Body("🔥 Serie: $streak ${if (streak == 1) "Tag" else "Tage"} in Folge unter $goal Min", C.text)
            }

            // 7 Tage
            Card {
                Text(if (hasUsage) "Minuten in Ablenkungs-Apps" else "Öffnungen pro Tag", color = C.text,
                    fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                if (!hasUsage) Body("Für Minuten brauchst du den Nutzungszugriff (Einrichtung auf dem Startbildschirm).")
                val values = days.map { if (hasUsage) it.minutes.coerceAtLeast(0) else it.opens }
                val max = (values.maxOrNull() ?: 0).coerceAtLeast(if (hasUsage) goal else 1)
                Row(Modifier.fillMaxWidth().height(160.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Bottom) {
                    days.forEachIndexed { i, d ->
                        val v = values[i]
                        val over = hasUsage && v > goal
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom,
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (d.known) "$v" else "–", color = C.dim, fontSize = 11.sp)
                            Box(Modifier.fillMaxWidth().fillMaxHeight((v.toFloat() / max).coerceIn(0.02f, 0.8f))
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(if (!d.known) C.line else if (over) C.bad else C.accent))
                            Text(d.label, color = C.dim, fontSize = 11.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
                if (hasUsage) Body("Grün = unter deinem Tagesziel von $goal Min, Rot = darüber.")
            }

            // Wochenvergleich / gesparte Zeit
            Card {
                Text("Diese Woche vs. letzte Woche", color = C.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                val saved = week.saved
                when {
                    saved == null -> Body("Der Vergleich erscheint, sobald die Handybremse 14 Tage Daten hat.")
                    saved > 0 -> Body("🎉 Du hast ${Stats.savedText(saved)} gespart!", C.accent)
                    saved == 0 -> Body("Genau gleich viel wie letzte Woche.")
                    else -> Body("${Stats.fmtMin(-saved)} mehr als letzte Woche. Nächste Woche schaffst du das!", C.warn)
                }
                if (week.thisWeek != null) Body("Letzte 7 Tage: ${Stats.fmtMin(week.thisWeek)}" +
                    (week.lastWeek?.let { " · davor: ${Stats.fmtMin(it)}" } ?: ""))
            }

            // Pro App (letzte 7 Tage)
            Card {
                Text("Öffnungen pro App (7 Tage)", color = C.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                val perApp = mutableMapOf<String, Int>()
                (0..6).forEach { off -> store.opensByApp(store.dayKey(off)).forEach { (k, v) -> perApp[k] = (perApp[k] ?: 0) + v } }
                if (perApp.values.sum() == 0) Body("Noch keine Öffnungen. 👏")
                perApp.entries.filter { it.value > 0 }.sortedByDescending { it.value }.forEach { (pkg, n) ->
                    Row {
                        Text(Apps.label(this@StatsActivity, pkg), color = C.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Text("$n×", color = C.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Body("Umentschieden insgesamt: ${store.declinedTotal()}× · Fokus geschafft: ${store.focusDone()}×")
            }

            // Fokus-Wald
            Card {
                val trees = store.forest()
                val alive = trees.count { it != "x" }
                Text("🌳 Dein Fokus-Wald", color = C.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                if (trees.isEmpty()) Body("Noch leer. Starte auf dem Startbildschirm einen Fokus, dann wächst hier dein erster Baum.")
                else {
                    Body("$alive ${if (alive == 1) "Baum" else "Bäume"} gewachsen" +
                        if (trees.size > alive) ", ${trees.size - alive} verwelkt" else "")
                    trees.takeLast(80).chunked(8).forEach { row ->
                        Text(row.joinToString(" ") { Stats.tree(it) }, fontSize = 26.sp, lineHeight = 34.sp)
                    }
                }
            }

            // Abzeichen
            Card {
                val earned = Stats.BADGES.count { store.badgeDate(it.id) != null }
                Text("🏅 Abzeichen ($earned von ${Stats.BADGES.size})", color = C.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Stats.BADGES.forEach { b ->
                    val date = store.badgeDate(b.id)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (date != null) b.emoji else "🔒", fontSize = 26.sp, modifier = Modifier.width(44.dp))
                        Column(Modifier.weight(1f)) {
                            Text(b.title, color = if (date != null) C.text else C.dim, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(b.text, color = C.dim, fontSize = 13.sp, lineHeight = 17.sp)
                        }
                    }
                }
            }

            BigButton("📋 Bilanz für Claude kopieren") {
                val text = Stats.summaryText(this@StatsActivity, store)
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Handybremse-Bilanz", text))
                Toast.makeText(this@StatsActivity, "Kopiert! Füg die Bilanz jetzt im Chat mit Claude ein.", Toast.LENGTH_LONG).show()
            }
            BigButton("Zurück", primary = false) { finish() }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Balken: verbrauchte Minuten gegen das Tagesziel. */
@Composable
fun GoalBar(minutes: Int, goal: Int) {
    val frac = (minutes.toFloat() / goal).coerceIn(0f, 1f)
    val over = minutes > goal
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("$minutes von $goal Min" + if (over) " – Ziel überschritten" else " – noch ${goal - minutes} Min",
            color = if (over) C.bad else C.text, fontSize = 15.sp)
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(C.line)) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(5.dp))
                .background(if (over) C.bad else if (frac > 0.8f) C.warn else C.accent))
        }
    }
}
