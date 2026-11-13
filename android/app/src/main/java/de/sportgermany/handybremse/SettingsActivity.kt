package de.sportgermany.handybremse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Einstellungen – nur offen, solange ein Freischalt-Code gilt. */
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HbTheme { SettingsScreen() } }
    }

    override fun onResume() {
        super.onResume()
        if (!Store(this).settingsUnlocked()) {
            LockActivity.start(this, "Die Einstellungen sind wieder gesperrt.")
            finish()
        }
    }

    @Composable
    private fun SettingsScreen() {
        val store = remember { Store(this) }
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(1_000)
                now = System.currentTimeMillis()
                if (!store.settingsUnlocked(now)) {
                    LockActivity.start(this@SettingsActivity, "Die 10 Minuten sind um. Die Einstellungen sind wieder gesperrt.")
                    finish()
                    break
                }
            }
        }
        var tab by remember { mutableIntStateOf(0) }
        val left = ((store.settingsUntil() - now) / 1000).coerceAtLeast(0)

        Column(
            Modifier.fillMaxSize().background(C.bg).safeDrawingPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Title("Einstellungen")
            Body("Noch %d:%02d Minuten offen".format(left / 60, left % 60), C.warn)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Gebremst", "Startseite", "Grenzen").forEachIndexed { i, t ->
                    Text(
                        t,
                        color = if (tab == i) C.bg else C.text,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (tab == i) C.accent else C.card)
                            .clickable { tab = i }
                            .padding(vertical = 10.dp),
                    )
                }
            }
            when (tab) {
                0 -> BlockedTab(store)
                1 -> FavoritesTab(store)
                else -> LimitsTab(store)
            }
        }
    }

    @Composable
    private fun AppChecklist(selected: Set<String>, onToggle: (String, Boolean) -> Unit) {
        val apps = remember { Apps.all(this, refresh = true) }
        LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(apps, key = { it.pkg }) { app ->
                val checked = app.pkg in selected
                Row(
                    Modifier.fillMaxWidth().clickable { onToggle(app.pkg, !checked) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = { onToggle(app.pkg, it) },
                        colors = CheckboxDefaults.colors(checkedColor = C.accent))
                    Text(app.label, color = C.text, fontSize = 17.sp)
                }
            }
        }
    }

    @Composable
    private fun BlockedTab(store: Store) {
        var blocked by remember { mutableStateOf(store.blocked) }
        Body("Diese Apps bekommen den Pause-Bildschirm:")
        AppChecklist(blocked) { pkg, on ->
            blocked = if (on) blocked + pkg else blocked - pkg
            store.blocked = blocked
        }
    }

    @Composable
    private fun FavoritesTab(store: Store) {
        var favs by remember { mutableStateOf(store.favorites ?: Apps.defaultFavorites(this)) }
        Body("Diese Apps stehen direkt auf dem Startbildschirm (höchstens ${Store.MAX_FAVORITES}). Alle anderen findest du über die Suche.")
        AppChecklist(favs.toSet()) { pkg, on ->
            favs = if (on) (if (favs.size < Store.MAX_FAVORITES) favs + pkg else favs) else favs - pkg
            store.favorites = favs
        }
    }

    @Composable
    private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(label, color = C.text, fontSize = 17.sp, modifier = Modifier.weight(1f))
            SmallBtn("–", onMinus)
            Text(value, color = C.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.width(80.dp))
            SmallBtn("+", onPlus)
        }
    }

    @Composable
    private fun SmallBtn(t: String, onClick: () -> Unit) {
        Text(t, color = C.text, fontSize = 22.sp, textAlign = TextAlign.Center,
            modifier = Modifier.width(48.dp).clip(RoundedCornerShape(10.dp)).background(C.line)
                .clickable(onClick = onClick).padding(vertical = 6.dp))
    }

    @Composable
    private fun LimitsTab(store: Store) {
        var limit by remember { mutableIntStateOf(store.dailyLimit) }
        var nightOn by remember { mutableStateOf(store.nightOn) }
        var from by remember { mutableIntStateOf(store.nightFrom) }
        var to by remember { mutableIntStateOf(store.nightTo) }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card {
                Stepper("Öffnungen pro App und Tag", "$limit×",
                    { limit = (limit - 1).coerceAtLeast(1); store.dailyLimit = limit },
                    { limit = (limit + 1).coerceAtMost(30); store.dailyLimit = limit })
            }
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Nachtruhe", color = C.text, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    Switch(checked = nightOn, onCheckedChange = { nightOn = it; store.nightOn = it })
                }
                if (nightOn) {
                    Stepper("Von", hhmm(from), { from = Math.floorMod(from - 30, 1440); store.nightFrom = from },
                        { from = Math.floorMod(from + 30, 1440); store.nightFrom = from })
                    Stepper("Bis", hhmm(to), { to = Math.floorMod(to - 30, 1440); store.nightTo = to },
                        { to = Math.floorMod(to + 30, 1440); store.nightTo = to })
                    Body("In der Nachtruhe sind alle gebremsten Apps gesperrt.")
                }
            }
            Spacer(Modifier.height(8.dp))
            BigButton("Fertig – wieder sperren", primary = true) {
                store.lockAll()
                Apps.goHome(this@SettingsActivity)
                finish()
            }
        }
    }
}
