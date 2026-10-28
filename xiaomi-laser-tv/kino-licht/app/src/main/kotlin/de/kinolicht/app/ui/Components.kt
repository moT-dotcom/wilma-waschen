@file:OptIn(ExperimentalLayoutApi::class)

package de.kinolicht.app.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.kinolicht.core.GradientRenderer
import de.kinolicht.core.GradientStyle
import de.kinolicht.core.LightAction
import de.kinolicht.core.Presets
import de.kinolicht.core.Rgb

/** Farben der App: dunkles Kino, orangefarbener Fokus. */
object KL {
    val bg = Color(0xFF0B0C10)
    val surface = Color(0xFF15171E)
    val surface2 = Color(0xFF1D2029)
    val selected = Color(0xFF2A1C10)
    val border = Color(0xFF2E323D)
    val text = Color(0xFFECEEF2)
    val muted = Color(0xFF9AA1AD)
    val accent = Color(0xFFFF7A1A)
    val ok = Color(0xFF4CC38A)
    val bad = Color(0xFFF2776C)
}

@Composable
fun KinoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = KL.accent,
            onPrimary = Color.Black,
            background = KL.bg,
            onBackground = KL.text,
            surface = KL.surface,
            onSurface = KL.text,
        ),
        typography = Typography(),
        content = content,
    )
}

fun Rgb.toColor(): Color = Color(argb)

/**
 * Grundbaustein für alles Anklickbare: wird beim Fokus größer und orange umrandet.
 * Auf dem Beamer ist der Fokus die einzige „Maus“ – er muss deutlich sichtbar sein.
 */
@Composable
fun FocusBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    shape: Shape = RoundedCornerShape(16.dp),
    background: Color = KL.surface,
    focusScale: Float = 1.05f,
    focusRequester: FocusRequester? = null,
    onFocus: (() -> Unit)? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) focusScale else 1f, label = "focusScale")
    LaunchedEffect(focused) { if (focused) onFocus?.invoke() }
    val borderColor = when {
        focused -> KL.accent
        selected -> KL.accent.copy(alpha = 0.5f)
        else -> Color.Transparent
    }
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(shape)
            .background(if (selected && !focused) KL.selected else if (focused) KL.surface2 else background)
            .border(2.dp, borderColor, shape)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = contentAlignment,
    ) { content(focused) }
}

@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    primary: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    FocusBox(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(26.dp),
        background = if (primary) KL.accent else KL.surface2,
        focusRequester = focusRequester,
    ) { focused ->
        Row(Modifier.padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Text(icon, fontSize = 18.sp)
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text,
                color = if (primary && !focused) Color.Black else KL.text,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun TvChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusBox(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        selected = selected,
        shape = RoundedCornerShape(23.dp),
        background = KL.surface2,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 18.dp),
            color = if (selected) KL.accent else KL.text,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, hint: String? = null) {
    Column(modifier.padding(top = 26.dp, bottom = 12.dp)) {
        Text(text, color = KL.text, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        if (hint != null) Text(hint, color = KL.muted, fontSize = 15.sp)
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(bottom = 6.dp)) {
        Text(title, color = KL.text, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, color = KL.muted, fontSize = 17.sp)
    }
}

/** Schieberegler fürs Steuerkreuz: ◀ ▶ ändern, gedrückt halten beschleunigt. */
@Composable
fun DpadSlider(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    step: Int = 1,
    valueText: (Int) -> String = { it.toString() },
    track: Brush? = null,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val current by rememberUpdatedState(value)
    val span = (range.last - range.first).coerceAtLeast(1)
    val fraction = ((value - range.first).toFloat() / span).coerceIn(0f, 1f)
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (focused) KL.surface2 else KL.surface)
            .border(2.dp, if (focused) KL.accent else Color.Transparent, shape)
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                val direction = when (e.key) {
                    Key.DirectionLeft -> -1
                    Key.DirectionRight -> 1
                    else -> return@onKeyEvent false
                }
                val fast = if (e.nativeKeyEvent.repeatCount > 6) 5 else 1
                onChange((current + direction * step * fast).coerceIn(range.first, range.last))
                true
            }
            .focusable(interactionSource = source)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = KL.muted, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text(valueText(value), color = KL.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.CenterStart) {
            val thumb = 24.dp
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(track ?: SolidColor(KL.border)),
            ) {
                if (track == null) {
                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(KL.accent))
                }
            }
            Box(
                Modifier
                    .offset(x = (maxWidth - thumb) * fraction)
                    .size(thumb)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(3.dp, if (focused) KL.accent else KL.border, CircleShape),
            )
        }
        if (focused) {
            Text("◀ ▶ ändern · gedrückt halten für schnell", color = KL.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    FocusBox(onClick = { onToggle(!checked) }, modifier = modifier.fillMaxWidth(), focusScale = 1.02f) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = KL.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                if (subtitle != null) Text(subtitle, color = KL.muted, fontSize = 15.sp)
            }
            Spacer(Modifier.width(16.dp))
            Box(
                Modifier
                    .size(width = 60.dp, height = 32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (checked) KL.accent else KL.border),
                contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(Modifier.padding(4.dp).size(24.dp).clip(CircleShape).background(Color.White))
            }
        }
    }
}

@Composable
fun SettingRow(title: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: String? = null) {
    FocusBox(onClick = onClick, modifier = modifier.fillMaxWidth(), focusScale = 1.02f) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Text(icon, fontSize = 22.sp)
                Spacer(Modifier.width(14.dp))
            }
            Text(title, color = KL.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(value, color = KL.muted, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 420.dp))
            Text("  ›", color = KL.muted, fontSize = 22.sp)
        }
    }
}

@Composable
fun InfoCard(text: String, modifier: Modifier = Modifier, icon: String = "💡", color: Color = KL.surface) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(color)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 22.sp)
        Spacer(Modifier.width(14.dp))
        Text(text, color = KL.text, fontSize = 16.sp)
    }
}

/** Vorschau eines Farbverlaufs, exakt so berechnet wie das Bild für die LEDs. */
@Composable
fun GradientPreview(colors: List<Rgb>, style: GradientStyle, modifier: Modifier = Modifier) {
    val bitmap = remember(colors, style) {
        val w = 48
        val h = 27
        val pixels = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            pixels[y * w + x] = GradientRenderer.colorAt(colors, style, (x + 0.5) / w, (y + 0.5) / h).argb.toInt()
        }
        Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    Image(bitmap, contentDescription = null, modifier = modifier, contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.Low)
}

private val rainbow = Brush.horizontalGradient(
    listOf(Color(0xFFFF3B30), Color(0xFFFF9500), Color(0xFFFFCC00), Color(0xFF34C759), Color(0xFF00C7FF), Color(0xFF5856D6), Color(0xFFAF52DE)),
)

/** Kleine Vorschau dessen, was eine Szene auf den LEDs zeigt. */
@Composable
fun ActionPreview(action: LightAction, modifier: Modifier = Modifier) {
    when (action) {
        is LightAction.Solid -> Box(modifier.background(action.color.toColor()))
        is LightAction.Gradient -> GradientPreview(action.colors, action.style, modifier)
        is LightAction.Effect -> Box(modifier.background(rainbow), contentAlignment = Alignment.Center) {
            Text("✨", fontSize = 14.sp)
        }
        LightAction.HyperionDefault -> Box(modifier.background(KL.border))
        LightAction.Off -> Box(modifier.background(Color.Black))
    }
}

@Composable
fun Swatch(color: Rgb, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(RoundedCornerShape(12.dp)).background(color.toColor()))
}

@Composable
fun TvDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .widthIn(min = 460.dp, max = 820.dp)
                .heightIn(max = 600.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(KL.surface)
                .border(1.dp, KL.border, RoundedCornerShape(24.dp))
                .padding(28.dp),
        ) {
            Text(title, color = KL.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    hint: String? = null,
) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    TvDialog(title, onDismiss) {
        if (hint != null) {
            Text(hint, color = KL.muted, fontSize = 15.sp)
            Spacer(Modifier.height(12.dp))
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            textStyle = TextStyle(fontSize = 20.sp),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onConfirm(text.trim()) }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = KL.text,
                unfocusedTextColor = KL.text,
                focusedBorderColor = KL.accent,
                unfocusedBorderColor = KL.border,
                cursorColor = KL.accent,
            ),
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton("Übernehmen", onClick = { onConfirm(text.trim()) }, primary = true, icon = "✓")
            TvButton("Abbrechen", onClick = onDismiss)
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    label: (T) -> String,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
    selected: T? = null,
) {
    val focus = remember { FocusRequester() }
    val initialIndex = options.indexOf(selected).coerceAtLeast(0)
    TvDialog(title, onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { index, option ->
                FocusBox(
                    onClick = { onPick(option) },
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    selected = option == selected,
                    focusScale = 1.02f,
                    focusRequester = if (index == initialIndex) focus else null,
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(label(option), modifier = Modifier.padding(horizontal = 20.dp), color = KL.text, fontSize = 18.sp)
                }
            }
            if (options.isEmpty()) Text("Keine Einträge", color = KL.muted, fontSize = 16.sp)
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

/** Farbwahl aus Palette, Weißtönen und Favoriten. */
@Composable
fun ColorPickDialog(favorites: List<Rgb>, onPick: (Rgb) -> Unit, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    TvDialog("Farbe wählen", onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (favorites.isNotEmpty()) {
                Text("Meine Farben", color = KL.muted, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    favorites.forEach { c -> SwatchButton(c, null) { onPick(c) } }
                }
                Spacer(Modifier.height(16.dp))
            }
            Text("Weißtöne", color = KL.muted, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Presets.whites.forEachIndexed { i, w ->
                    val c = Rgb.fromKelvin(w.kelvin)
                    SwatchButton(c, w.name, if (i == 0 && favorites.isEmpty()) focus else null) { onPick(c) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Farben", color = KL.muted, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Presets.palette.forEach { n -> SwatchButton(n.color, n.name) { onPick(n.color) } }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
fun SwatchButton(color: Rgb, name: String?, focusRequester: FocusRequester? = null, size: Dp = 72.dp, selected: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FocusBox(
            onClick = onClick,
            modifier = Modifier.size(size),
            selected = selected,
            shape = RoundedCornerShape(18.dp),
            background = color.toColor(),
            focusScale = 1.12f,
            focusRequester = focusRequester,
        ) { focused ->
            // Fokus-Hintergrund nicht über die Farbe legen
            Box(Modifier.fillMaxSize().padding(if (focused) 4.dp else 0.dp).clip(RoundedCornerShape(14.dp)).background(color.toColor()))
        }
        if (name != null) {
            Text(name, color = KL.muted, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp).width(size + 12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

/** Uhrzeit-Auswahl: OK zum Bearbeiten, ▲▼ ändern, ◀▶ zwischen Stunde und Minute. */
@Composable
fun TimeSpinner(hour: Int, minute: Int, onChange: (Int, Int) -> Unit, modifier: Modifier = Modifier) {
    var editing by remember { mutableStateOf(false) }
    var part by remember { mutableIntStateOf(0) }
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val h by rememberUpdatedState(hour)
    val m by rememberUpdatedState(minute)
    LaunchedEffect(focused) { if (!focused) editing = false }
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (focused) KL.surface2 else KL.surface)
            .border(2.dp, if (focused) KL.accent else Color.Transparent, shape)
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                val confirm = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                if (!editing) {
                    if (confirm) { editing = true; part = 0; true } else false
                } else {
                    val fast = if (e.nativeKeyEvent.repeatCount > 4) 5 else 1
                    when {
                        e.key == Key.DirectionUp || e.key == Key.DirectionDown -> {
                            val d = if (e.key == Key.DirectionUp) fast else -fast
                            if (part == 0) onChange((h + d).mod(24), m) else onChange(h, (m + d).mod(60))
                            true
                        }
                        e.key == Key.DirectionLeft -> { part = 0; true }
                        e.key == Key.DirectionRight -> { part = 1; true }
                        confirm || e.key == Key.Back -> { editing = false; true }
                        else -> false
                    }
                }
            }
            .focusable(interactionSource = source)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TimePart("%02d".format(hour), editing && part == 0)
            Text(":", color = KL.text, fontSize = 40.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp))
            TimePart("%02d".format(minute), editing && part == 1)
        }
        Text(
            if (editing) "▲▼ ändern · ◀▶ wechseln · OK fertig" else if (focused) "OK drücken zum Ändern" else " ",
            color = KL.muted,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun TimePart(text: String, active: Boolean) {
    Text(
        text,
        color = if (active) Color.Black else KL.text,
        fontSize = 40.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) KL.accent else Color.Transparent)
            .padding(horizontal = 8.dp),
    )
}
