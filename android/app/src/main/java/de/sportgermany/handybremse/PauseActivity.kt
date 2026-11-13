package de.sportgermany.handybremse

import android.content.Context
import android.content.Intent
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Der Pause-Bildschirm: „Wofür?“ und „Wie lange?“, bevor eine gebremste App aufgeht. */
class PauseActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_PKG = "pkg"
        private const val EXTRA_EXPIRED = "expired"

        fun start(context: Context, pkg: String, expired: Boolean) {
            context.startActivity(Intent(context, PauseActivity::class.java)
                .putExtra(EXTRA_PKG, pkg)
                .putExtra(EXTRA_EXPIRED, expired)
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
    }

    private var screenKey by mutableStateOf(0)

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
        val key = screenKey
        setContent {
            HbTheme {
                androidx.compose.runtime.key(key) { PauseScreen(pkg, expired) }
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

    @Composable
    private fun PauseScreen(pkg: String, expired: Boolean) {
        val store = remember { Store(this) }
        val name = remember { Apps.label(this, pkg) }
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
                store.isNight() -> {
                    Title("Nachtruhe 🌙")
                    Body("$name ist bis ${hhmm(store.nightTo)} Uhr gesperrt. Leg das Handy weg und gönn dir die Ruhe.")
                    Spacer(Modifier.height(12.dp))
                    BigButton("Zum Startbildschirm") { leave() }
                }

                store.limitReached(pkg) -> {
                    if (expired) Body("Deine Zeit ist um.", C.warn)
                    Title("Für heute ist Schluss mit $name")
                    Body("Du hast $name heute schon $opens-mal geöffnet. Dein Limit ist $limit-mal am Tag. Morgen geht es wieder.")
                    Spacer(Modifier.height(12.dp))
                    BigButton("Zum Startbildschirm") { leave() }
                }

                intention == null -> {
                    if (expired) {
                        Title("Deine Zeit ist um ⏰")
                        store.intention(pkg)?.let { Body("Du wolltest: „$it“. Hast du das geschafft?") }
                        Body("Wenn du weitermachen willst, fängst du von vorne an.")
                    } else {
                        Title("Du willst $name öffnen.")
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
                        Card {
                            Body("Ehrliche Antwort 👍", C.accent)
                            Body("Vielleicht lieber kurz aufstehen, ein Glas Wasser trinken oder rausgehen? Du kannst es dir hier noch anders überlegen.")
                        }
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
                    Body("Wenn die Zeit um ist, schließt die Handybremse $name. Danach geht es nur von vorne weiter.")
                    Spacer(Modifier.height(8.dp))
                    BigButton("Doch nicht", primary = true) { decline() }
                    BigButton("Zurück", primary = false) { intention = null }
                }
            }
        }
    }
}
