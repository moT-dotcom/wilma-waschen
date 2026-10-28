@file:OptIn(ExperimentalLayoutApi::class)

package de.kinolicht.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.kinolicht.core.GradientStyle
import de.kinolicht.core.LightAction
import de.kinolicht.core.Presets
import de.kinolicht.core.Rgb
import de.kinolicht.core.Scene
import kotlin.math.roundToInt

private enum class ColorTab(val label: String) { PALETTE("Palette"), WHITE("Weißtöne"), FINE("Feinwahl"), GRADIENT("Farbverlauf") }

@Composable
fun ColorsScreen(vm: AppViewModel) {
    var tab by rememberSaveable { mutableStateOf(ColorTab.PALETTE) }
    var saveAction by remember { mutableStateOf<LightAction?>(null) }
    val workColor by vm.workColor.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 48.dp)) {
        ScreenTitle("Farben", "Jede Änderung leuchtet sofort – probier einfach aus")
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ColorTab.entries.forEach { t -> TvChip(t.label, selected = t == tab, onClick = { tab = t }) }
        }
        Spacer(Modifier.height(8.dp))
        when (tab) {
            ColorTab.PALETTE -> PaletteTab(vm, onSave = { saveAction = LightAction.Solid(workColor) })
            ColorTab.WHITE -> WhiteTab(vm, onSave = { saveAction = LightAction.Solid(workColor) })
            ColorTab.FINE -> FineTab(vm, onSave = { saveAction = LightAction.Solid(workColor) })
            ColorTab.GRADIENT -> GradientTab(vm, onSave = { saveAction = it })
        }
    }

    saveAction?.let { action ->
        TextInputDialog(
            title = "Als Szene speichern",
            initial = "Meine Szene",
            hint = "Die Szene erscheint danach auf der Startseite und im Zeitplan.",
            onDismiss = { saveAction = null },
            onConfirm = { name ->
                if (name.isNotBlank()) {
                    val icon = if (action is LightAction.Gradient) "🌈" else "🎨"
                    vm.saveScene(Scene(name = name, icon = icon, action = action))
                }
                saveAction = null
            },
        )
    }
}

@Composable
private fun PaletteTab(vm: AppViewModel, onSave: () -> Unit) {
    val config by vm.config.collectAsStateWithLifecycle()
    val workColor by vm.workColor.collectAsStateWithLifecycle()
    val isFavorite = workColor in config.favoriteColors

    if (config.favoriteColors.isNotEmpty()) {
        SectionTitle("Meine Farben")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            config.favoriteColors.forEach { c ->
                SwatchButton(c, null, size = 84.dp, selected = c == workColor) { vm.preview(LightAction.Solid(c)) }
            }
        }
    }
    SectionTitle("Palette")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Presets.palette.forEach { n ->
            SwatchButton(n.color, n.name, size = 84.dp, selected = n.color == workColor) {
                vm.preview(LightAction.Solid(n.color), n.name)
            }
        }
    }
    ColorActions(vm, workColor, isFavorite, onSave)
}

@Composable
private fun WhiteTab(vm: AppViewModel, onSave: () -> Unit) {
    val config by vm.config.collectAsStateWithLifecycle()
    val workColor by vm.workColor.collectAsStateWithLifecycle()
    var kelvin by rememberSaveable { mutableIntStateOf(2700) }

    SectionTitle("Weißtöne", hint = "Warmes Licht entspannt, kühles wirkt wach")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Presets.whites.forEach { w ->
            val c = Rgb.fromKelvin(w.kelvin)
            SwatchButton(c, w.name, size = 84.dp, selected = kelvin == w.kelvin) {
                kelvin = w.kelvin
                vm.preview(LightAction.Solid(c), w.name)
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    DpadSlider(
        label = "Farbtemperatur",
        value = kelvin,
        range = 1500..6500,
        step = 100,
        onChange = {
            kelvin = it
            vm.preview(LightAction.Solid(Rgb.fromKelvin(it)), "$it K")
        },
        valueText = { "$it K" },
        track = Brush.horizontalGradient((1500..6500 step 1000).map { Rgb.fromKelvin(it).toColor() }),
        modifier = Modifier.fillMaxWidth(),
    )
    ColorActions(vm, workColor, workColor in config.favoriteColors, onSave)
}

@Composable
private fun FineTab(vm: AppViewModel, onSave: () -> Unit) {
    val config by vm.config.collectAsStateWithLifecycle()
    val workColor by vm.workColor.collectAsStateWithLifecycle()
    val start = remember { workColor.toHsv() }
    var hue by rememberSaveable { mutableIntStateOf(start.hue.roundToInt()) }
    var sat by rememberSaveable { mutableIntStateOf((start.saturation * 100).roundToInt()) }
    var value by rememberSaveable { mutableIntStateOf((start.value * 100).roundToInt().coerceAtLeast(5)) }
    fun push() = vm.preview(LightAction.Solid(Rgb.fromHsv(hue.toDouble(), sat / 100.0, value / 100.0)), "Eigene Farbe")

    SectionTitle("Feinwahl", hint = "Farbton, Sättigung und Leuchtkraft einzeln einstellen")
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DpadSlider(
                label = "Farbton",
                value = hue,
                range = 0..359,
                step = 3,
                onChange = { hue = it; push() },
                valueText = { "$it°" },
                track = Brush.horizontalGradient((0..360 step 30).map { Rgb.fromHsv(it.toDouble(), 1.0, 1.0).toColor() }),
                modifier = Modifier.fillMaxWidth(),
            )
            DpadSlider(
                label = "Sättigung",
                value = sat,
                range = 0..100,
                step = 2,
                onChange = { sat = it; push() },
                valueText = { "$it %" },
                track = Brush.horizontalGradient(listOf(Rgb.WHITE.toColor(), Rgb.fromHsv(hue.toDouble(), 1.0, 1.0).toColor())),
                modifier = Modifier.fillMaxWidth(),
            )
            DpadSlider(
                label = "Leuchtkraft",
                value = value,
                range = 1..100,
                step = 2,
                onChange = { value = it; push() },
                valueText = { "$it %" },
                track = Brush.horizontalGradient(listOf(Rgb.BLACK.toColor(), Rgb.fromHsv(hue.toDouble(), sat / 100.0, 1.0).toColor())),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Swatch(workColor, 180.dp)
            Spacer(Modifier.height(8.dp))
            Text(workColor.hex, color = KL.muted, fontSize = 16.sp)
        }
    }
    ColorActions(vm, workColor, workColor in config.favoriteColors, onSave)
}

@Composable
private fun ColorActions(vm: AppViewModel, color: Rgb, isFavorite: Boolean, onSave: () -> Unit) {
    Spacer(Modifier.height(24.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        TvButton(if (isFavorite) "Aus Favoriten entfernen" else "Als Favorit merken", onClick = { vm.toggleFavoriteColor(color) }, icon = if (isFavorite) "★" else "☆")
        TvButton("Als Szene speichern", onClick = onSave, icon = "💾")
    }
}

@Composable
private fun GradientTab(vm: AppViewModel, onSave: (LightAction) -> Unit) {
    val config by vm.config.collectAsStateWithLifecycle()
    var colors by remember { mutableStateOf(Presets.gradients.first().colors) }
    var style by remember { mutableStateOf(GradientStyle.AROUND) }
    var editIndex by remember { mutableStateOf<Int?>(null) }
    fun push() = vm.preview(LightAction.Gradient(colors, style), "Farbverlauf")

    SectionTitle("Vorlagen", hint = "Mehrfarbige Szenen rund um die Leinwand")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Presets.gradients.forEach { g ->
            FocusBox(
                onClick = {
                    colors = g.colors
                    style = g.style
                    vm.preview(LightAction.Gradient(g.colors, g.style), g.name)
                },
                modifier = Modifier.size(width = 190.dp, height = 120.dp),
                shape = RoundedCornerShape(20.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                Column {
                    GradientPreview(g.colors, g.style, Modifier.fillMaxWidth().height(62.dp))
                    Text("${g.icon}  ${g.name}", color = KL.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(14.dp))
                }
            }
        }
    }

    SectionTitle("Eigener Verlauf")
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Anordnung", color = KL.muted, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GradientStyle.entries.forEach { s -> TvChip(s.label, selected = s == style, onClick = { style = s; push() }) }
            }
            Spacer(Modifier.height(18.dp))
            Text("Farben (OK zum Ändern)", color = KL.muted, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                colors.forEachIndexed { i, c -> SwatchButton(c, "Farbe ${i + 1}", size = 72.dp) { editIndex = i } }
                if (colors.size < 4) TvButton("Farbe", onClick = { colors = colors + colors.last(); push() }, icon = "+")
                if (colors.size > 2) TvButton("Farbe", onClick = { colors = colors.dropLast(1); push() }, icon = "−")
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GradientPreview(colors, style, Modifier.size(width = 288.dp, height = 162.dp).clip(RoundedCornerShape(16.dp)))
            Spacer(Modifier.height(8.dp))
            Text("So sehen es die LEDs", color = KL.muted, fontSize = 14.sp)
        }
    }
    Spacer(Modifier.height(24.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TvButton("Anwenden", onClick = { push() }, primary = true, icon = "▶")
        TvButton("Als Szene speichern", onClick = { onSave(LightAction.Gradient(colors, style)) }, icon = "💾")
    }

    editIndex?.let { index ->
        ColorPickDialog(
            favorites = config.favoriteColors,
            onPick = { picked ->
                colors = colors.toMutableList().also { it[index] = picked }
                editIndex = null
                push()
            },
            onDismiss = { editIndex = null },
        )
    }
}
