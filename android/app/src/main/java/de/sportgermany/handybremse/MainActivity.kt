package de.sportgermany.handybremse

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.runtime.produceState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Der ruhige Startbildschirm. */
class MainActivity : ComponentActivity() {

    private var resumeTick by mutableIntStateOf(0)
    private var query by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { query = "" }
        })
        setContent { HbTheme { Home() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        query = ""   // Home-Taste: Suche zurücksetzen
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }

    @Composable
    private fun Home() {
        val store = remember { Store(this) }
        @Suppress("UNUSED_EXPRESSION") resumeTick
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(if (store.focusActive()) 1_000 else 15_000)
                now = System.currentTimeMillis()
            }
        }

        var apps by remember { mutableStateOf(Apps.cached() ?: emptyList()) }
        var favList by remember { mutableStateOf(store.favorites ?: emptyList()) }
        LaunchedEffect(resumeTick) {
            // App-Liste im Hintergrund laden, damit der Startbildschirm sofort erscheint
            apps = withContext(Dispatchers.Default) {
                Apps.all(this@MainActivity, refresh = true).also {
                    if (store.favorites == null) store.favorites = Apps.defaultFavorites(this@MainActivity)
                }
            }
            favList = store.favorites ?: emptyList()
        }
        val labels = remember(apps) { apps.associate { it.pkg to it.label } }
        val favorites = favList.filter { it in labels }
        val blocked = store.blocked

        Column(
            Modifier
                .fillMaxSize()
                .background(C.bg)
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(now)),
                color = C.text, fontSize = 64.sp, fontWeight = FontWeight.Light)
            Text(SimpleDateFormat("EEEE, d. MMMM", Locale.GERMANY).format(Date(now)),
                color = C.dim, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            StatsBlock(store, now)
            Spacer(Modifier.height(16.dp))
            FocusBlock(store, now)
            Spacer(Modifier.height(8.dp))

            SetupCard()

            if (query.isBlank()) {
                favorites.forEach { pkg ->
                    AppRow(labels.getValue(pkg), pkg in blocked) { Apps.open(this@MainActivity, pkg) }
                }
            }

            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("App suchen …") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (query.isNotBlank()) {
                val q = query.trim().lowercase()
                val hits = apps.filter { it.label.lowercase().contains(q) }.take(12)
                if (hits.isEmpty()) Body("Keine App gefunden.")
                hits.forEach { app ->
                    AppRow(app.label, app.pkg in blocked) { query = ""; Apps.open(this@MainActivity, app.pkg) }
                }
            }

            Spacer(Modifier.height(32.dp))
            Text("📊 Statistik & Abzeichen", color = C.dim, fontSize = 15.sp,
                modifier = Modifier.clickable { startActivity(Intent(this@MainActivity, StatsActivity::class.java)) }
                    .padding(vertical = 8.dp))
            Text("🛠 Hilfe & Reparatur" + if (HbApp.lastCrash(this@MainActivity) != null) " – Fehlerbericht da" else "",
                color = C.dim, fontSize = 15.sp,
                modifier = Modifier.clickable { startActivity(Intent(this@MainActivity, RepairActivity::class.java)) }
                    .padding(vertical = 8.dp))
            val open = store.settingsUnlocked(now)
            val openText = if (!open) "🔒 Einstellungen"
                else "🔓 Einstellungen – noch offen bis " + SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(store.settingsUntil()))
            Text(openText, color = if (open) C.warn else C.dim, fontSize = 15.sp,
                modifier = Modifier.clickable {
                    if (store.settingsUnlocked()) startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                    else LockActivity.start(this@MainActivity)
                }.padding(vertical = 8.dp))
        }
    }

    /** Tagesziel, Serie, neue Abzeichen und Sonntags der Wochen-Rückblick. */
    @Composable
    private fun StatsBlock(store: Store, now: Long) {
        // Im Hintergrund: Minuten holen, Fokus zählen, Abzeichen prüfen
        val refreshed by produceState(initialValue = 0, resumeTick) {
            withContext(Dispatchers.Default) {
                Stats.refreshMinutes(this@MainActivity, store)
                if (store.collectFinishedFocus()) withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "🎉 Fokus geschafft!", Toast.LENGTH_LONG).show()
                }
                Stats.checkBadges(store)
            }
            value = resumeTick + 1
        }
        @Suppress("UNUSED_EXPRESSION") refreshed
        @Suppress("UNUSED_EXPRESSION") now
        var dismissTick by remember { mutableIntStateOf(0) }
        @Suppress("UNUSED_EXPRESSION") dismissTick

        val today = store.dayKey()
        val minutes = store.minutes(today)
        val declined = store.declinedToday()
        val streak = Stats.streak(store)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (Apps.hasUsageAccess(this@MainActivity) && minutes >= 0) GoalBar(minutes, store.goalFor())
            val parts = mutableListOf<String>()
            if (streak > 0) parts += "🔥 $streak ${if (streak == 1) "Tag" else "Tage"} unter Ziel"
            if (declined > 0) parts += "${declined}× umentschieden 🎉"
            if (parts.isNotEmpty()) Text(parts.joinToString(" · "), color = C.accent, fontSize = 15.sp)

            store.takeNewBadge()?.let { id -> Stats.badge(id) }?.let { b ->
                Card {
                    Text("${b.emoji} Neues Abzeichen: ${b.title}", color = C.accent, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Body(b.text)
                    BigButton("Super!", primary = false) { store.clearNewBadge(); dismissTick++ }
                }
            }

            if (Stats.isSunday() && !store.reviewDismissed(today)) {
                val w = Stats.week(store)
                Card {
                    Text("📅 Dein Wochen-Rückblick", color = C.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    val last7 = (0..6).map { store.dayKey(it) }
                    Body("Letzte 7 Tage: " + (w.thisWeek?.let { Stats.fmtMin(it) } ?: "noch zu wenig Daten") +
                        " · ${last7.sumOf { store.opensAll(it) }}× geöffnet · ${last7.sumOf { store.declined(it) }}× umentschieden")
                    w.saved?.let {
                        if (it > 0) Body("🎉 ${Stats.savedText(it)} weniger als letzte Woche!", C.accent)
                        else if (it < 0) Body("${Stats.fmtMin(-it)} mehr als letzte Woche. Neue Woche, neue Chance!", C.warn)
                    }
                    Body("🔥 Serie: $streak Tage", C.text)
                    BigButton("Statistik ansehen", primary = false) {
                        startActivity(Intent(this@MainActivity, StatsActivity::class.java))
                    }
                    BigButton("Okay", primary = false) { store.dismissReview(today); dismissTick++ }
                }
            }
        }
    }

    /** Fokus-Timer: freiwillig starten, dann sind alle gebremsten Apps komplett zu. */
    @Composable
    private fun FocusBlock(store: Store, now: Long) {
        var choosing by remember { mutableStateOf(false) }
        var confirmCancel by remember { mutableStateOf(false) }
        var tick by remember { mutableIntStateOf(0) }
        @Suppress("UNUSED_EXPRESSION") tick
        if (store.focusActive(now)) {
            val left = ((store.focusUntil() - now) / 1000).coerceAtLeast(0)
            val total = store.focusMinutes() * 60f
            val progress = (1f - left / total).coerceIn(0f, 1f)
            Card {
                Text("🎯 Fokus", color = C.accent, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Stats.growing(progress, store.focusMinutes()), fontSize = 56.sp)
                    Spacer(Modifier.width(16.dp))
                    Text("%d:%02d".format(left / 60, left % 60), color = C.text, fontSize = 44.sp, fontWeight = FontWeight.Light)
                }
                Body("Dein Baum wächst, solange du dranbleibst. Alle gebremsten Apps sind bis zum Ende komplett zu.")
                if (!confirmCancel) {
                    Text("Fokus abbrechen", color = C.dim, fontSize = 14.sp,
                        modifier = Modifier.clickable { confirmCancel = true }.padding(vertical = 4.dp))
                } else {
                    Body("Wirklich abbrechen? Dann welkt dein Baum 🥀 und der Fokus zählt nicht.", C.warn)
                    BigButton("Weitermachen 💪") { confirmCancel = false }
                    BigButton("Ja, abbrechen", primary = false) { store.cancelFocus(); confirmCancel = false; tick++ }
                }
            }
        } else if (choosing) {
            Card {
                Text("🎯 Wie lange Fokus?", color = C.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Body("In dieser Zeit sind alle gebremsten Apps komplett zu, auch Reels und Shorts. Dafür wächst ein Baum in deinem Wald: 25 Min 🌿 · 45 Min 🌳 · 60 Min 🌲 · 90 Min 🌴")
                listOf(25, 45, 60, 90).forEach { m ->
                    BigButton("$m Minuten", primary = false) { store.startFocus(m); choosing = false; tick++ }
                }
                Text("Abbrechen", color = C.dim, fontSize = 14.sp,
                    modifier = Modifier.clickable { choosing = false }.padding(vertical = 4.dp))
            }
        } else {
            store.activeBlock(now)?.let { b ->
                Text("⛔ Sperrzeit „${b.name}“ bis ${hhmm(b.to)} Uhr", color = C.warn, fontSize = 15.sp)
            }
            Text("🎯 Fokus starten", color = C.accent, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { choosing = true }.padding(vertical = 6.dp))
        }
    }

    @Composable
    private fun AppRow(label: String, isBlocked: Boolean, onClick: () -> Unit) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = if (isBlocked) C.dim else C.text, fontSize = 26.sp, modifier = Modifier.weight(1f))
            if (isBlocked) Text("⏸", color = C.dim, fontSize = 18.sp)
        }
    }

    // ---------- Einrichtung ----------

    @Composable
    private fun SetupCard() {
        val launcher = Apps.isDefaultLauncher(this)
        val usage = Apps.hasUsageAccess(this)
        val battery = Apps.ignoresBattery(this)
        val guard = Apps.isGuardEnabled(this)
        if (launcher && guard) return

        Card(Modifier.padding(bottom = 16.dp)) {
            Text("Einrichtung", color = C.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Body("Bitte genau in dieser Reihenfolge. Der Wächter kommt zuletzt, ab dann ist die Handybremse geschützt.")

            Step(1, "Als Startbildschirm festlegen", launcher) {
                startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            }
            Step(2, "Nutzungszugriff erlauben (für die Statistik, freiwillig)", usage) {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            Step(3, "Akku: Handybremse auf „Nicht optimiert“ stellen", battery) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
            if (!guard) {
                Body("4. Wächter einschalten. Ist der Schalter grau („Eingeschränkte Einstellung“)? Dann einmal draufgetippt lassen, zurück, App-Info öffnen, oben rechts ⋮ → „Eingeschränkte Einstellungen zulassen“, danach nochmal.", C.text)
                BigButton("App-Info öffnen", primary = false) {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                }
            }
            Step(4, "Wächter einschalten (Bedienungshilfen → Installierte Apps → Handybremse)", guard) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
    }

    @Composable
    private fun Step(n: Int, text: String, done: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
        if (done) {
            Body("✅ $n. $text", C.accent)
        } else {
            BigButton("$n. $text", primary = enabled, enabled = enabled, onClick = onClick)
        }
    }
}
