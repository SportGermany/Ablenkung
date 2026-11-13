package de.sportgermany.handybremse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object C {
    val bg = Color(0xFF0E1116)
    val card = Color(0xFF181C23)
    val line = Color(0xFF2A303A)
    val text = Color(0xFFE8ECEF)
    val dim = Color(0xFF8E98A4)
    val accent = Color(0xFF7FD1AE)
    val accentDark = Color(0xFF1F3A34)
    val warn = Color(0xFFF2B872)
    val bad = Color(0xFFEF8A80)
}

@Composable
fun HbTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.accent, onPrimary = C.bg, background = C.bg, surface = C.card,
            onBackground = C.text, onSurface = C.text,
        ),
        content = content,
    )
}

@Composable
fun BigButton(text: String, primary: Boolean = true, enabled: Boolean = true, onClick: () -> Unit) {
    val bg = if (!enabled) C.card else if (primary) C.accent else C.card
    val fg = if (!enabled) C.dim else if (primary) C.bg else C.text
    Text(
        text,
        color = fg,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, if (primary && enabled) C.accent else C.line, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 16.dp),
    )
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(C.card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.Start,
        content = content,
    )
}

@Composable
fun Title(text: String) = Text(text, color = C.text, fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp)

@Composable
fun Body(text: String, color: Color = C.dim) = Text(text, color = color, fontSize = 16.sp, lineHeight = 22.sp)

fun hhmm(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)
