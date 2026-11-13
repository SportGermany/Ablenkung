package de.sportgermany.handybremse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Einstellungen – nur offen, solange ein Freischalt-Code gilt. */
class SettingsActivity : ComponentActivity() {

    private lateinit var store: Store

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        setContent { HbTheme { SettingsScreen() } }
    }

    override fun onResume() {
        super.onResume()
        if (!store.settingsUnlocked()) {
            LockActivity.start(this, "Die Einstellungen sind gesperrt.")
            finish()
        }
    }

    override fun onStop() {
        super.onStop()
        // Code „nur solange du drin bleibst“: beim Verlassen sofort wieder sperren
        if (!isChangingConfigurations && !store.settingsSticky()) {
            store.lockSettings()
            finish()
        }
    }

    private fun closeAndLock() {
        store.lockSettings()
        Apps.goHome(this)
        finish()
    }

    @Composable
    private fun SettingsScreen() {
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(1_000)
                now = System.currentTimeMillis()
                if (!store.settingsUnlocked(now)) {
                    LockActivity.start(this@SettingsActivity, "Die Zeit ist um. Die Einstellungen sind wieder gesperrt.")
                    finish()
                    break
                }
            }
        }
        var tab by remember { mutableIntStateOf(0) }
        val apps by produceState(initialValue = Apps.cached() ?: emptyList()) {
            value = withContext(Dispatchers.Default) { Apps.all(this@SettingsActivity, refresh = true) }
        }
        val sticky = store.settingsSticky()
        val left = ((store.settingsUntil() - now) / 1000).coerceAtLeast(0)

        Column(Modifier.fillMaxSize().background(C.bg).safeDrawingPadding().imePadding()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Title("Einstellungen")
                Text(
                    if (sticky) "🔓 Offen bis ${SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(store.settingsUntil()))} Uhr " +
                        "(noch %d:%02d) – auch wenn du zwischendurch rausgehst".format(left / 60, left % 60)
                    else "🔓 Offen, solange du hier bleibst. Wenn du rausgehst, wird wieder gesperrt.",
                    color = C.warn, fontSize = 14.sp, lineHeight = 19.sp,
                )
                val tabs = listOf("⏸ Gebremst", "🏠 Startseite", "💡 Alternativen", "⏰ Grenzen & Ziel")
                tabs.chunked(2).forEachIndexed { row, pair -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEachIndexed { j, t ->
                        val i = row * 2 + j
                        Text(
                            t,
                            color = if (tab == i) C.bg else C.text,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (tab == i) C.accent else C.card)
                                .clickable { tab = i }
                                .padding(vertical = 12.dp),
                        )
                    }
                } }
            }
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> BlockedTab(apps)
                    1 -> FavoritesTab(apps)
                    2 -> AlternativesTab(apps)
                    else -> LimitsTab()
                }
            }
            Box(Modifier.padding(16.dp)) {
                BigButton("✓ Fertig – wieder sperren") { closeAndLock() }
            }
        }
    }

    // ---------- Bausteine ----------

    @Composable
    private fun Info(text: String) {
        Card { Body(text, C.text) }
    }

    @Composable
    private fun SectionHeader(text: String) {
        Text(text, color = C.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
    }

    @Composable
    private fun SearchField(query: String, onChange: (String) -> Unit) {
        OutlinedTextField(
            value = query, onValueChange = onChange, singleLine = true,
            placeholder = { Text("App suchen …") }, modifier = Modifier.fillMaxWidth(),
        )
    }

    @Composable
    private fun CheckRow(label: String, checked: Boolean, enabled: Boolean = true, onToggle: (Boolean) -> Unit) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .clickable(enabled = enabled) { onToggle(!checked) }.padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = if (enabled) onToggle else null, enabled = enabled,
                colors = CheckboxDefaults.colors(checkedColor = C.accent))
            Text(label, color = if (enabled) C.text else C.dim, fontSize = 17.sp)
        }
    }

    private fun LazyListScope.appSearchList(
        apps: List<AppInfo>, query: String, exclude: Set<String>,
        row: @Composable (AppInfo) -> Unit,
    ) {
        val q = query.trim().lowercase()
        val list = apps.filter { it.pkg !in exclude && (q.isEmpty() || it.label.lowercase().contains(q)) }
        items(list, key = { "all_" + it.pkg }) { row(it) }
        if (apps.isEmpty()) item { Body("Apps werden geladen …") }
    }

    // ---------- Tab: Gebremste Apps ----------

    @Composable
    private fun BlockedTab(apps: List<AppInfo>) {
        var blocked by remember { mutableStateOf(store.blocked) }
        var query by remember { mutableStateOf("") }
        fun set(pkg: String, on: Boolean) {
            blocked = if (on) blocked + pkg else blocked - pkg
            store.blocked = blocked
        }
        var shortsOnly by remember { mutableStateOf(store.shortsOnly) }
        val chosen = apps.filter { it.pkg in blocked }
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp), modifier = Modifier.fillMaxSize()) {
            item { Info("Vor diesen Apps kommt immer die Pause mit „Wofür?“ und „Wie lange?“. Häkchen setzen = App wird gebremst. Bei Instagram und YouTube kannst du einstellen, dass nur Reels bzw. Shorts gebremst werden. „Zeit pro Tag“ gibt jeder App ein eigenes Minuten-Limit. Die Bremse gilt auch für die Websites im Browser (tiktok.com, instagram.com, youtube.com).") }
            item { SectionHeader("GEBREMST (${chosen.size})") }
            if (chosen.isEmpty()) item { Body("Noch keine App gebremst.") }
            items(chosen, key = { "sel_" + it.pkg }) { app ->
                Column {
                    CheckRow(app.label, true) { set(app.pkg, it) }
                    if (app.pkg in Shorts.SUPPORTED) {
                        val on = app.pkg in shortsOnly
                        Row(Modifier.fillMaxWidth().padding(start = 48.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Nur ${Shorts.name(app.pkg)} bremsen", color = C.text, fontSize = 15.sp)
                                Text(if (on) "Der Rest der App geht ohne Pause" else "Aus: die ganze App wird gebremst",
                                    color = C.dim, fontSize = 13.sp)
                            }
                            Switch(checked = on, onCheckedChange = {
                                shortsOnly = if (it) shortsOnly + app.pkg else shortsOnly - app.pkg
                                store.shortsOnly = shortsOnly
                            })
                        }
                    }
                    var appLim by remember(app.pkg) { mutableIntStateOf(store.appLimit(app.pkg)) }
                    Row(Modifier.fillMaxWidth().padding(start = 48.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Zeit pro Tag", color = C.text, fontSize = 15.sp)
                            Text(if (appLim == 0) "Kein eigenes Limit" else "Höchstens $appLim Min am Tag",
                                color = C.dim, fontSize = 13.sp)
                        }
                        IconBtn("–", enabled = appLim > 0) { appLim = (appLim - 5).coerceAtLeast(0); store.setAppLimit(app.pkg, appLim) }
                        Text(if (appLim == 0) "aus" else "$appLim", color = C.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center, modifier = Modifier.width(48.dp))
                        IconBtn("+") { appLim = (appLim + 5).coerceAtMost(300); store.setAppLimit(app.pkg, appLim) }
                    }
                }
            }
            item { SectionHeader("WEITERE APPS") }
            item { SearchField(query) { query = it } }
            appSearchList(apps, query, blocked) { app -> CheckRow(app.label, false) { set(app.pkg, it) } }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    // ---------- Tab: Startbildschirm ----------

    @Composable
    private fun FavoritesTab(apps: List<AppInfo>) {
        var favs by remember { mutableStateOf(store.favorites ?: emptyList()) }
        var query by remember { mutableStateOf("") }
        val max = Store.MAX_FAVORITES
        val full = favs.size >= max
        fun save(v: List<String>) { favs = v; store.favorites = v }
        val labels = apps.associate { it.pkg to it.label }

        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp), modifier = Modifier.fillMaxSize()) {
            item {
                Card {
                    Text("${favs.size} von $max Plätzen belegt", color = if (full) C.warn else C.accent,
                        fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Body("Auf dem Startbildschirm stehen höchstens $max Apps, damit er ruhig bleibt. Alle anderen Apps findest du dort über die Suche.", C.text)
                }
            }
            item { SectionHeader("AUF DEM STARTBILDSCHIRM – Reihenfolge mit ↑ ↓") }
            if (favs.isEmpty()) item { Body("Noch keine App ausgewählt.") }
            itemsIndexed(favs, key = { _, p -> "fav_$p" }) { i, pkg ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}.", color = C.dim, fontSize = 16.sp, modifier = Modifier.width(28.dp))
                    Text(labels[pkg] ?: pkg, color = C.text, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    IconBtn("↑", enabled = i > 0) { save(favs.toMutableList().apply { add(i - 1, removeAt(i)) }) }
                    IconBtn("↓", enabled = i < favs.size - 1) { save(favs.toMutableList().apply { add(i + 1, removeAt(i)) }) }
                    IconBtn("✕") { save(favs - pkg) }
                }
            }
            item { SectionHeader("APP HINZUFÜGEN") }
            if (full) item { Body("Alle $max Plätze sind belegt. Entferne oben zuerst eine App mit ✕, dann kannst du eine neue hinzufügen.", C.warn) }
            item { SearchField(query) { query = it } }
            appSearchList(apps, query, favs.toSet()) { app ->
                CheckRow(app.label, false, enabled = !full) { if (it) save(favs + app.pkg) }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    // ---------- Tab: Alternativen ----------

    @Composable
    private fun AlternativesTab(apps: List<AppInfo>) {
        var alts by remember { mutableStateOf(store.altApps) }
        var query by remember { mutableStateOf("") }
        val max = 4
        val full = alts.size >= max
        fun save(v: List<String>) { alts = v; store.altApps = v }
        val labels = apps.associate { it.pkg to it.label }
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp), modifier = Modifier.fillMaxSize()) {
            item {
                Card {
                    Text("${alts.size} von $max Alternativ-Apps", color = if (full) C.warn else C.accent,
                        fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Body("Wenn du bei der Pause „Mir ist langweilig“ oder „Aus Gewohnheit“ wählst, schlägt die Handybremse dir diese Apps vor, z. B. eine Lern-, Lese- oder Sport-App. Der Planer ist immer dabei.", C.text)
                }
            }
            item { SectionHeader("DEINE ALTERNATIVEN") }
            item { Body("📚 Planer (immer dabei)", C.text) }
            items(alts, key = { "alt_$it" }) { pkg ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(labels[pkg] ?: pkg, color = C.text, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    IconBtn("✕") { save(alts - pkg) }
                }
            }
            item { SectionHeader("APP HINZUFÜGEN") }
            if (full) item { Body("Alle $max Plätze sind belegt. Entferne oben zuerst eine App mit ✕.", C.warn) }
            item { SearchField(query) { query = it } }
            appSearchList(apps, query, alts.toSet() + store.blocked) { app ->
                CheckRow(app.label, false, enabled = !full) { if (it) save(alts + app.pkg) }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    @Composable
    private fun IconBtn(t: String, enabled: Boolean = true, onClick: () -> Unit) {
        Text(t, color = if (enabled) C.text else C.line, fontSize = 18.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(start = 6.dp).width(40.dp).clip(RoundedCornerShape(10.dp))
                .background(C.card).clickable(enabled = enabled, onClick = onClick).padding(vertical = 6.dp))
    }

    // ---------- Tab: Grenzen ----------

    @Composable
    private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(label, color = C.text, fontSize = 17.sp, modifier = Modifier.weight(1f))
            IconBtn("–", onClick = onMinus)
            Text(value, color = C.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.width(76.dp))
            IconBtn("+", onClick = onPlus)
        }
    }

    @Composable
    private fun LimitsTab() {
        var limit by remember { mutableIntStateOf(store.dailyLimit) }
        var goal by remember { mutableIntStateOf(store.dailyGoal) }
        var nightOn by remember { mutableStateOf(store.nightOn) }
        var from by remember { mutableIntStateOf(store.nightFrom) }
        var to by remember { mutableIntStateOf(store.nightTo) }
        var weekend by remember { mutableStateOf(store.weekendDifferent) }
        var wLimit by remember { mutableIntStateOf(store.weekendLimit) }
        var wGoal by remember { mutableIntStateOf(store.weekendGoal) }
        var blocks by remember { mutableStateOf(store.blocks) }
        fun saveBlocks(v: List<Store.Block>) { blocks = v; store.blocks = v }
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()) {
            item {
                Card {
                    Text(if (weekend) "Tagesziel 🎯 (Mo–Fr)" else "Tagesziel 🎯", color = C.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Body("Höchstens so viele Minuten am Tag in gebremsten Apps. Danach richten sich Balken, Serie 🔥 und Abzeichen.")
                    Stepper("Minuten pro Tag", "$goal Min",
                        { goal = (goal - 5).coerceAtLeast(5); store.dailyGoal = goal },
                        { goal = (goal + 5).coerceAtMost(300); store.dailyGoal = goal })
                }
            }
            item {
                Card {
                    Text(if (weekend) "Wie oft am Tag? (Mo–Fr)" else "Wie oft am Tag?", color = C.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Body("So oft darfst du jede gebremste App pro Tag öffnen. Danach ist sie bis Mitternacht gesperrt.")
                    Stepper("Öffnungen pro App", "$limit×",
                        { limit = (limit - 1).coerceAtLeast(1); store.dailyLimit = limit },
                        { limit = (limit + 1).coerceAtMost(30); store.dailyLimit = limit })
                }
            }
            item {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Nachtruhe 🌙", color = C.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f))
                        Switch(checked = nightOn, onCheckedChange = { nightOn = it; store.nightOn = it })
                    }
                    Body("In dieser Zeit sind alle gebremsten Apps komplett gesperrt.")
                    if (nightOn) {
                        Stepper("Von", hhmm(from), { from = Math.floorMod(from - 30, 1440); store.nightFrom = from },
                            { from = Math.floorMod(from + 30, 1440); store.nightFrom = from })
                        Stepper("Bis", hhmm(to), { to = Math.floorMod(to - 30, 1440); store.nightTo = to },
                            { to = Math.floorMod(to + 30, 1440); store.nightTo = to })
                    }
                }
            }
            item {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Wochenende anders 🎉", color = C.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f))
                        Switch(checked = weekend, onCheckedChange = { weekend = it; store.weekendDifferent = it })
                    }
                    Body("Eigenes Tagesziel und eigenes Limit für Samstag und Sonntag.")
                    if (weekend) {
                        Stepper("Ziel Sa/So", "$wGoal Min",
                            { wGoal = (wGoal - 5).coerceAtLeast(5); store.weekendGoal = wGoal },
                            { wGoal = (wGoal + 5).coerceAtMost(300); store.weekendGoal = wGoal })
                        Stepper("Öffnungen Sa/So", "$wLimit×",
                            { wLimit = (wLimit - 1).coerceAtLeast(1); store.weekendLimit = wLimit },
                            { wLimit = (wLimit + 1).coerceAtMost(30); store.weekendLimit = wLimit })
                    }
                }
            }
            item {
                Card {
                    Text("Sperrzeiten ⛔", color = C.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Body("Feste Zeiten, z. B. Schule oder Training. Dann sind alle gebremsten Apps komplett zu, wie in der Nachtruhe.")
                    if (blocks.isEmpty()) Body("Noch keine Sperrzeit.", C.text)
                    blocks.forEachIndexed { i, b ->
                        fun upd(nb: Store.Block) = saveBlocks(blocks.toMutableList().apply { set(i, nb) })
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.bg).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(b.name, color = C.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                IconBtn("✕") { saveBlocks(blocks.toMutableList().apply { removeAt(i) }) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEachIndexed { d, label ->
                                    val on = b.days.getOrNull(d) == '1'
                                    Text(label, color = if (on) C.bg else C.dim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                            .background(if (on) C.accent else C.card)
                                            .clickable {
                                                val chars = b.days.padEnd(7, '0').toCharArray()
                                                chars[d] = if (on) '0' else '1'
                                                upd(b.copy(days = String(chars)))
                                            }.padding(vertical = 8.dp))
                                }
                            }
                            Stepper("Von", hhmm(b.from), { upd(b.copy(from = Math.floorMod(b.from - 15, 1440))) },
                                { upd(b.copy(from = Math.floorMod(b.from + 15, 1440))) })
                            Stepper("Bis", hhmm(b.to), { upd(b.copy(to = Math.floorMod(b.to - 15, 1440))) },
                                { upd(b.copy(to = Math.floorMod(b.to + 15, 1440))) })
                            if (b.from >= b.to) Body("„Bis“ muss nach „Von“ liegen.", C.warn)
                        }
                    }
                    Body("Neue Sperrzeit hinzufügen:")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            Store.Block("Schule", "1111100", 8 * 60, 13 * 60),
                            Store.Block("Training", "1010100", 17 * 60, 19 * 60),
                            Store.Block("Lernen", "1111000", 15 * 60, 17 * 60),
                        ).forEach { preset ->
                            Text("+ ${preset.name}", color = C.text, fontSize = 14.sp, textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(C.line)
                                    .clickable { saveBlocks(blocks + preset) }.padding(vertical = 10.dp))
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
