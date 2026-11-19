package edu.virginia.cs4720.eta.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

// Wireframe palette: grayscale, black outlines, dark grey for selection.
val Ink = Color(0xFF333333)
val Quiet = Color(0xFF555555)
val Line = Color(0xFF000000)
val Fill = Color(0xFF555555)
val Tint = Color(0xFFEEEEEE)

private val Square = RoundedCornerShape(0.dp)

@Composable
fun EtaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Fill,
            onPrimary = Color.White,
            primaryContainer = Tint,
            onPrimaryContainer = Ink,
            secondary = Quiet,
            onSecondary = Color.White,
            secondaryContainer = Tint,
            onSecondaryContainer = Ink,
            background = Color.White,
            onBackground = Ink,
            surface = Color.White,
            onSurface = Ink,
            surfaceVariant = Tint,
            onSurfaceVariant = Quiet,
            surfaceContainer = Color.White,
            outline = Line,
            error = Ink,
        ),
        shapes = Shapes(extraSmall = Square, small = Square, medium = Square, large = Square, extraLarge = Square),
        content = content,
    )
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = RectangleShape,
        border = BorderStroke(1.dp, Line),
        colors = ButtonDefaults.buttonColors(containerColor = Fill, contentColor = Color.White),
    ) { Text(text, fontWeight = FontWeight.Bold) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = RectangleShape,
        border = BorderStroke(1.dp, Line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink),
    ) { Text(text, fontWeight = FontWeight.Bold) }
}

/** A row of boxes where exactly one is selected (filled dark grey). */
@Composable
fun ChoiceRow(
    options: List<Pair<String, String>>,   // value to label
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().border(1.dp, Line)) {
        options.forEachIndexed { i, (value, label) ->
            val on = value == selected
            if (i > 0) Box(Modifier.width(1.dp).height(44.dp).background(Line))
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .background(if (on) Fill else Color.White)
                    .selectable(selected = on, onClick = { onSelect(value) }, role = Role.RadioButton),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (on) Color.White else Ink, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

/** A single on/off box (used for repeat days). */
@Composable
fun ToggleBox(label: String, on: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(44.dp)
            .border(1.dp, Line)
            .background(if (on) Fill else Color.White)
            .toggleable(value = on, onValueChange = { onToggle() }, role = Role.Checkbox),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 13.sp, color = if (on) Color.White else Ink, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
}

/** Dashed-style message box for errors and notices (grayscale, no color). */
@Composable
fun NoticeBox(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().border(1.dp, Quiet).background(Tint).padding(10.dp)) {
        Text(text, fontSize = 14.sp, color = Ink)
    }
}

object Fmt {
    private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    fun time(t: LocalTime): String = t.format(timeFormat)

    /** Distances in miles (feet when short). */
    fun distance(meters: Double): String {
        val ft = meters * 3.28084
        return if (ft < 528) "${(ft / 10).roundToInt() * 10} ft"
        else String.format(Locale.US, "%.2f mi", meters / 1609.344)
    }

    fun minutes(seconds: Long): String {
        val m = ceil(seconds / 60.0).toLong().coerceAtLeast(0)
        return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
    }

    fun mode(mode: String): String = when (mode) {
        "bike" -> "Bike"
        "bus" -> "Bus"
        else -> "Walk"
    }
}

/** Real share into other apps (Messages, email, etc.). */
fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Share ETA"))
}
