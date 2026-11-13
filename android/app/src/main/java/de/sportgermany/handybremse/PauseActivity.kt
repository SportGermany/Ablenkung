package de.sportgermany.handybremse

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Der Pause-Bildschirm: „Wofür?“ und „Wie lange?“, bevor eine gebremste App aufgeht. */
class PauseActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_PKG = "pkg"
        private const val EXTRA_EXPIRED = "expired"
        private const val EXTRA_SHORTS = "shorts"
        const val PLANER_URL = "https://sportgermany.github.io/Schulplaner/"

        fun start(context: Context, pkg: String, expired: Boolean, shorts: Boolean = false) {
            context.startActivity(Intent(context, PauseActivity::class.java)
                .putExtra(EXTRA_PKG, pkg)
                .putExtra(EXTRA_EXPIRED, expired)
                .putExtra(EXTRA_SHORTS, shorts)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }

        val INTENTIONS = listOf(
            "Eine Nachricht beantworten",
            "Etwas Bestimmtes anschauen",
            "Bewusst entspannen",
            "Mir ist langweilig",
            "Einfach aus Gewohnheit",
        )
        val HONEST = setOf("Mir ist langweilig", "Einfach aus Gewohnheit")

        val IDEAS = listOf(
            "Ans Fenster gehen und 5-mal tief durchatmen",
            "Ein Glas Wasser trinken",
            "10 Kniebeugen oder 20 Hampelmänner",
            "Jemandem schreiben, den du magst",
            "2 Minuten aufräumen",
            "Ein Lied bewusst anhören, ohne nebenbei etwas anderes",
            "Kurz rausgehen, einmal um den Block",
            "Dehnen: Nacken, Schultern, Rücken",
            "Etwas für die Schule oder das Training vorbereiten",
            "Eine Seite in einem Buch lesen",
        )
    }

    private var screenKey by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = decline()
        })
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        screenKey++
        render()
    }

    private fun render() {
        val pkg = intent.getStringExtra(EXTRA_PKG) ?: run { finish(); return }
        val expired = intent.getBooleanExtra(EXTRA_EXPIRED, false)
        val shorts = intent.getBooleanExtra(EXTRA_SHORTS, false)
        val store = Store(this)
        if (store.isNight()) store.addNightTry()
        val key = screenKey
        setContent {
            HbTheme {
                androidx.compose.runtime.key(key) { PauseScreen(pkg, expired, shorts) }
            }
        }
    }

    private fun decline() {
        Store(this).addDeclined()
        Apps.goHome(this)
        finish()
    }

    private fun leave() {
        Apps.goHome(this)
        finish()
    }

    private fun openAlternative(target: String, countAsDecline: Boolean) {
        if (countAsDecline) Store(this).addDeclined()
        if (target == PLANER_URL) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PLANER_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) { Apps.goHome(this) }
        } else if (!Apps.launchDirect(this, target)) Apps.goHome(this)
        finish()
    }

    /** Vorschläge, was man stattdessen machen kann. */
    @Composable
    private fun Alternatives(store: Store, countAsDecline: Boolean) {
        val ideas = remember { IDEAS.shuffled().take(2) }
        Card {
            Text("💡 Wie wäre es stattdessen mit …", color = C.accent, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            ideas.forEach { Body("• $it", C.text) }
            BigButton("📚 Planer öffnen", primary = false) { openAlternative(PLANER_URL, countAsDecline) }
            store.altApps.forEach { pkg ->
                BigButton(Apps.label(this@PauseActivity, pkg) + " öffnen", primary = false) { openAlternative(pkg, countAsDecline) }
            }
        }
    }

    @Composable
    private fun PauseScreen(pkg: String, expired: Boolean, shorts: Boolean) {
        val store = remember { Store(this) }
        val appName = remember { Apps.label(this, pkg) }
        val what = if (shorts) "${Shorts.name(pkg)} in $appName" else appName
        var intention by remember { mutableStateOf<String?>(null) }

        Column(
            Modifier
                .fillMaxSize()
                .background(C.bg)
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            val opens = store.opensToday(pkg)
            val limit = store.dailyLimit

            when {
                store.focusActive() -> {
                    val left = ((store.focusUntil() - System.currentTimeMillis()) / 60_000L + 1).coerceAtLeast(1)
                    Title("🎯 Du bist im Fokus")
                    Body("Noch $left Minuten. Bis dahin ist $appName zu. Du schaffst das!")
                    Alternatives(store, countAsDecline = false)
                    BigButton("Zurück zum Fokus") { leave() }
                }

                store.isNight() -> {
                    Title("Nachtruhe 🌙")
                    Body("$appName ist bis ${hhmm(store.nightTo)} Uhr gesperrt. Leg das Handy weg und gönn dir die Ruhe.")
                    Spacer(Modifier.height(4.dp))
                    BigButton("Zum Startbildschirm") { leave() }
                }

                store.limitReached(pkg) -> {
                    if (expired) Body("Deine Zeit ist um.", C.warn)
                    Title("Für heute ist Schluss mit $appName")
                    Body("Du hast $appName heute schon $opens-mal geöffnet. Dein Limit ist $limit-mal am Tag. Morgen geht es wieder.")
                    Alternatives(store, countAsDecline = false)
                    BigButton("Zum Startbildschirm") { leave() }
                }

                intention == null -> {
                    if (expired) {
                        Title("Deine Zeit ist um ⏰")
                        store.intention(pkg)?.let { Body("Du wolltest: „$it“. Hast du das geschafft?") }
                        if (store.canExtend(pkg)) {
                            BigButton("+5 Minuten (nur einmal, zählt als Öffnung)", primary = false) {
                                store.extend(pkg)
                                if (!Apps.launchDirect(this@PauseActivity, pkg)) Apps.goHome(this@PauseActivity)
                                finish()
                            }
                        }
                        Body("Sonst fängst du von vorne an:")
                    } else {
                        Title("Du willst $what öffnen.")
                    }
                    Body("Wofür?", C.text)
                    INTENTIONS.forEach { option ->
                        BigButton(option, primary = false) { intention = option }
                    }
                    Body("Heute schon $opens von $limit Mal geöffnet.")
                    Spacer(Modifier.height(8.dp))
                    BigButton("Doch nicht", primary = true) { decline() }
                }

                else -> {
                    Title("Wie lange?")
                    if (intention in HONEST) {
                        Body("Ehrliche Antwort 👍 Du kannst es dir hier noch anders überlegen.", C.accent)
                        Alternatives(store, countAsDecline = true)
                    } else {
                        Body("„$intention“")
                    }
                    listOf(5, 10, 15).forEach { min ->
                        BigButton("$min Minuten", primary = false) {
                            store.startSession(pkg, min, intention!!)
                            if (!Apps.launchDirect(this@PauseActivity, pkg)) Apps.goHome(this@PauseActivity)
                            finish()
                        }
                    }
                    Body("Wenn die Zeit um ist, schließt die Handybremse $what. Danach kannst du einmal um 5 Minuten verlängern oder fängst von vorne an.")
                    Spacer(Modifier.height(8.dp))
                    BigButton("Doch nicht", primary = true) { decline() }
                    BigButton("Zurück", primary = false) { intention = null }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
