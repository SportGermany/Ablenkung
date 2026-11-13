package de.sportgermany.handybremse

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Freischalten: Anfrage-Nummer an Claude schicken, Code eingeben – oder Notfall-Code. */
class LockActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_MSG = "msg"
        fun start(context: Context, message: String? = null) {
            context.startActivity(Intent(context, LockActivity::class.java)
                .putExtra(EXTRA_MSG, message)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }

        /** Öffnet die App-Info der Handybremse, dort auf „Deinstallieren“ tippen (der Wächter lässt es dann zu). */
        fun startUninstall(context: Context) {
            Toast.makeText(context, "Tippe jetzt auf „Deinstallieren“.", Toast.LENGTH_LONG).show()
            Handler(Looper.getMainLooper()).postDelayed({
                context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }, 600)
        }
    }

    private var refresh by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HbTheme { LockScreen() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        refresh++
    }

    override fun onResume() {
        super.onResume()
        refresh++
    }

    private fun copy(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Anfrage-Nummer", text))
        Toast.makeText(this, "Kopiert: $text", Toast.LENGTH_SHORT).show()
    }

    @Composable
    private fun LockScreen() {
        val store = remember { Store(this) }
        @Suppress("UNUSED_EXPRESSION") refresh
        val message = intent.getStringExtra(EXTRA_MSG)
        var feedback by remember { mutableStateOf<String?>(null) }
        var emergencyDone by remember { mutableStateOf(false) }
        val locked = false   // keine Sperre nach Falschversuchen

        fun tryCode(action: Char, input: String): Boolean {
            val ok = Codes.check(action, store.challenge(action), input) ||
                (action == Codes.SETTINGS && Codes.check(Codes.SETTINGS_WINDOW, store.challenge(action), input))
            if (ok) {
                store.renewChallenge(action)
                feedback = null
            } else {
                feedback = "Der Code passt nicht zu ${action}-${store.challenge(action)}. Prüf, ob du ihn im richtigen Feld eingegeben hast. Du kannst es einfach nochmal versuchen."
            }
            return ok
        }

        Column(
            Modifier
                .fillMaxSize()
                .background(C.bg)
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Title("Freischalten 🔒")
            message?.let { Body(it, C.warn) }
            Body("Schick die Anfrage-Nummer an Claude. Claude fragt dich, wofür und warum, und gibt dir dann einen Code.")
            feedback?.let { Body(it, C.bad) }

            if (emergencyDone) {
                Card {
                    Body("Notfall-Code richtig ✅", C.accent)
                    Body("Alles ist für 30 Minuten freigeschaltet.")
                    BigButton("Einstellungen öffnen") {
                        startActivity(Intent(this@LockActivity, SettingsActivity::class.java))
                    }
                    BigButton("Handybremse jetzt entfernen", primary = false) { startUninstall(this@LockActivity) }
                }
            }

            ActionCard(
                title = "Einstellungen ändern",
                hint = "Claude gibt dir einen von zwei Codes: Einer öffnet die Einstellungen, solange du drin bleibst. Der andere lässt sie ${Store.SETTINGS_MINUTES} Minuten offen, auch wenn du zwischendurch auf den Startbildschirm gehst.",
                action = Codes.SETTINGS,
                store = store,
                enabled = !locked,
            ) { input ->
                val window = Codes.check(Codes.SETTINGS_WINDOW, store.challenge(Codes.SETTINGS), input)
                if (tryCode(Codes.SETTINGS, input)) {
                    if (window) store.unlockSettings(Store.SETTINGS_MINUTES, sticky = true)
                    else store.unlockSettings(30, sticky = false)
                    startActivity(Intent(this@LockActivity, SettingsActivity::class.java))
                    finish()
                }
            }

            ActionCard(
                title = "Handybremse entfernen",
                hint = "Gibt die Deinstallation für ${Store.UNINSTALL_MINUTES} Minuten frei und öffnet die App-Info.",
                action = Codes.UNINSTALL,
                store = store,
                enabled = !locked,
            ) { input ->
                if (tryCode(Codes.UNINSTALL, input)) {
                    store.unlockUninstall()
                    startUninstall(this@LockActivity)
                }
            }

            Card {
                Text("Notfall-Code", color = C.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Body("Nur für den Notfall, wenn du Claude nicht erreichen kannst. Der Code steht in deinen Notizen.")
                var input by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(24) },
                    singleLine = true,
                    placeholder = { Text("XXXX-XXXX-XXXX-XXXX") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth(),
                )
                BigButton("Notfall-Code prüfen", primary = false, enabled = !locked && input.isNotBlank()) {
                    if (Codes.isEmergency(input)) {
                        store.unlockSettings(30, sticky = true)
                        store.unlockUninstall(30)
                        emergencyDone = true
                        feedback = null
                        input = ""
                    } else {
                        feedback = "Der Notfall-Code stimmt nicht. Prüf ihn in deinen Notizen und versuch es nochmal."
                    }
                }
            }

            BigButton("Zurück zum Startbildschirm", primary = false) { Apps.goHome(this@LockActivity); finish() }
            Spacer(Modifier.height(24.dp))
        }
    }

    @Composable
    private fun ActionCard(
        title: String,
        hint: String,
        action: Char,
        store: Store,
        enabled: Boolean,
        onSubmit: (String) -> Unit,
    ) {
        val request = "$action-${store.challenge(action)}"
        var input by remember(request) { mutableStateOf("") }
        Card {
            Text(title, color = C.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Body(hint)
            Body("Anfrage-Nummer:")
            Text(request, color = C.accent, fontSize = 30.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            BigButton("Nummer kopieren", primary = false) { copy(request) }
            OutlinedTextField(
                value = input,
                onValueChange = { v -> input = v.filter { it.isDigit() }.take(6) },
                singleLine = true,
                label = { Text("Code von Claude (6 Ziffern)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            BigButton("Freischalten", enabled = enabled && input.length == 6) { onSubmit(input) }
        }
    }
}
