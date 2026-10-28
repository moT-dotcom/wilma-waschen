@file:OptIn(ExperimentalLayoutApi::class)

package de.kinolicht.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.kinolicht.core.GradientStyle
import de.kinolicht.core.LightAction
import de.kinolicht.core.Presets
import de.kinolicht.core.Rgb
import de.kinolicht.core.Scene

@Composable
fun ScenesScreen(vm: AppViewModel) {
    val config by vm.config.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Scene?>(null) }

    val current = editing
    if (current != null) {
        SceneEditor(
            vm = vm,
            initial = current,
            isNew = config.scenes.none { it.id == current.id },
            onClose = { editing = null },
        )
    } else {
        SceneOverview(vm, onEdit = { editing = it })
    }
}

@Composable
private fun SceneOverview(vm: AppViewModel, onEdit: (Scene) -> Unit) {
    val config by vm.config.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 48.dp)) {
        ScreenTitle("Szenen", "OK startet eine Szene · ✎ darunter bearbeitet sie")
        Spacer(Modifier.height(18.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            config.scenes.forEach { scene ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SceneCard(scene, active = config.lastScene?.id == scene.id, onClick = { vm.applyScene(scene) })
                    Spacer(Modifier.height(8.dp))
                    FocusBox(
                        onClick = { onEdit(scene) },
                        modifier = Modifier.size(width = 190.dp, height = 40.dp),
                        shape = RoundedCornerShape(12.dp),
                        background = KL.surface2,
                    ) { Text("✎ Bearbeiten", color = KL.muted, fontSize = 14.sp) }
                }
            }
            AddCard("Neue Szene") {
                onEdit(Scene(name = "Neue Szene", icon = "💡", action = LightAction.Solid(Rgb.fromKelvin(2700)), brightness = 60))
            }
        }
    }
}

private enum class SceneType(val label: String) { COLOR("Farbe"), GRADIENT("Verlauf"), EFFECT("Effekt"), HYPERION("Hyperion"), OFF("Aus") }

private fun LightAction.type(): SceneType = when (this) {
    is LightAction.Solid -> SceneType.COLOR
    is LightAction.Gradient -> SceneType.GRADIENT
    is LightAction.Effect -> SceneType.EFFECT
    LightAction.HyperionDefault -> SceneType.HYPERION
    LightAction.Off -> SceneType.OFF
}

@Composable
private fun SceneEditor(vm: AppViewModel, initial: Scene, isNew: Boolean, onClose: () -> Unit) {
    val config by vm.config.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    var scene by remember { mutableStateOf(initial) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    // Zuletzt gewählte Werte je Typ merken, damit Hin- und Herschalten nichts verliert.
    var color by remember { mutableStateOf((initial.action as? LightAction.Solid)?.color ?: Rgb.fromKelvin(2700)) }
    var gradient by remember { mutableStateOf((initial.action as? LightAction.Gradient) ?: LightAction.Gradient(Presets.gradients.first().colors)) }
    var effect by remember { mutableStateOf((initial.action as? LightAction.Effect)?.name ?: state?.effects?.firstOrNull() ?: "Rainbow swirl") }
    var gradientIndex by remember { mutableStateOf<Int?>(null) }
    val firstFocus = remember { FocusRequester() }

    BackHandler(onBack = onClose)

    fun setType(type: SceneType) {
        scene = scene.copy(
            action = when (type) {
                SceneType.COLOR -> LightAction.Solid(color)
                SceneType.GRADIENT -> gradient
                SceneType.EFFECT -> LightAction.Effect(effect)
                SceneType.HYPERION -> LightAction.HyperionDefault
                SceneType.OFF -> LightAction.Off
            },
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 48.dp)) {
        ScreenTitle(if (isNew) "Neue Szene" else "Szene bearbeiten", "Zurück-Taste verwirft Änderungen")
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FocusBox(
                    onClick = { dialog = "name" },
                    modifier = Modifier.fillMaxWidth(),
                    focusScale = 1.02f,
                    focusRequester = firstFocus,
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Name", color = KL.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(scene.name, color = KL.muted, fontSize = 17.sp)
                        Text("  ›", color = KL.muted, fontSize = 22.sp)
                    }
                }
                SettingRow("Symbol", scene.icon, onClick = { dialog = "icon" })
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SceneCard(scene, active = false, onClick = { vm.applyScene(scene) })
                Text("OK = Vorschau", color = KL.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }

        SectionTitle("Was soll leuchten?")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SceneType.entries.forEach { t -> TvChip(t.label, selected = scene.action.type() == t, onClick = { setType(t) }) }
        }
        Spacer(Modifier.height(16.dp))
        when (val action = scene.action) {
            is LightAction.Solid -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SwatchButton(action.color, null, size = 84.dp) { dialog = "color" }
                Text("${action.color.hex} · OK zum Ändern", color = KL.muted, fontSize = 16.sp)
            }
            is LightAction.Gradient -> Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GradientStyle.entries.forEach { s ->
                        TvChip(s.label, selected = s == action.style, onClick = {
                            gradient = action.copy(style = s)
                            scene = scene.copy(action = gradient)
                        })
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    action.colors.forEachIndexed { i, c -> SwatchButton(c, "Farbe ${i + 1}", size = 72.dp) { gradientIndex = i } }
                    if (action.colors.size < 4) TvButton("Farbe", icon = "+", onClick = {
                        gradient = action.copy(colors = action.colors + action.colors.last())
                        scene = scene.copy(action = gradient)
                    })
                    if (action.colors.size > 2) TvButton("Farbe", icon = "−", onClick = {
                        gradient = action.copy(colors = action.colors.dropLast(1))
                        scene = scene.copy(action = gradient)
                    })
                    GradientPreview(action.colors, action.style, Modifier.size(width = 160.dp, height = 90.dp).clip(RoundedCornerShape(12.dp)))
                }
            }
            is LightAction.Effect -> SettingRow("Effekt", Presets.effectLabel(action.name), onClick = { dialog = "effect" }, icon = "✨")
            LightAction.HyperionDefault -> InfoCard("Gibt die LEDs an Hyperion zurück – es läuft wieder, was dort als Standard eingestellt ist.", icon = "↩")
            LightAction.Off -> InfoCard("Schaltet die LED-Ausgabe aus.", icon = "⏻")
        }

        if (scene.action != LightAction.Off) {
            SectionTitle("Helligkeit")
            ToggleRow(
                title = "Helligkeit festlegen",
                subtitle = if (scene.brightness == null) "Aus: aktuelle Helligkeit bleibt" else "Szene setzt eigene Helligkeit",
                checked = scene.brightness != null,
                onToggle = { on -> scene = scene.copy(brightness = if (on) 60 else null) },
            )
            scene.brightness?.let { b ->
                Spacer(Modifier.height(10.dp))
                DpadSlider("☀ Helligkeit", b, 1..100, onChange = { scene = scene.copy(brightness = it) }, step = 5, valueText = { "$it %" }, modifier = Modifier.fillMaxWidth())
            }
        }

        Spacer(Modifier.height(28.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton("Speichern", primary = true, icon = "💾", onClick = {
                vm.saveScene(scene)
                onClose()
            })
            TvButton("Testen", icon = "▶", onClick = { vm.applyScene(scene) })
            if (!isNew) {
                TvButton("Nach vorne", icon = "◀", onClick = { vm.moveScene(scene.id, -1) })
                TvButton("Nach hinten", icon = "▶", onClick = { vm.moveScene(scene.id, 1) })
                TvButton("Löschen", icon = "🗑", onClick = { confirmDelete = true })
            }
            TvButton("Abbrechen", onClick = onClose)
        }
    }

    when (dialog) {
        "name" -> TextInputDialog("Name der Szene", scene.name, onDismiss = { dialog = null }, onConfirm = {
            if (it.isNotBlank()) scene = scene.copy(name = it)
            dialog = null
        })
        "icon" -> TvDialog("Symbol wählen", onDismiss = { dialog = null }) {
            val focus = remember { FocusRequester() }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Presets.sceneIcons.forEachIndexed { i, icon ->
                    FocusBox(
                        onClick = { scene = scene.copy(icon = icon); dialog = null },
                        modifier = Modifier.size(72.dp),
                        selected = icon == scene.icon,
                        focusScale = 1.12f,
                        focusRequester = if (i == 0) focus else null,
                    ) { Text(icon, fontSize = 32.sp) }
                }
            }
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        }
        "color" -> ColorPickDialog(config.favoriteColors, onPick = {
            color = it
            scene = scene.copy(action = LightAction.Solid(it))
            dialog = null
        }, onDismiss = { dialog = null })
        "effect" -> ChoiceDialog(
            title = "Effekt wählen",
            options = state?.effects.orEmpty(),
            label = { Presets.effectLabel(it) },
            selected = effect,
            onPick = {
                effect = it
                scene = scene.copy(action = LightAction.Effect(it))
                dialog = null
            },
            onDismiss = { dialog = null },
        )
    }

    gradientIndex?.let { index ->
        ColorPickDialog(config.favoriteColors, onPick = { picked ->
            val g = scene.action as? LightAction.Gradient
            if (g != null) {
                gradient = g.copy(colors = g.colors.toMutableList().also { it[index] = picked })
                scene = scene.copy(action = gradient)
            }
            gradientIndex = null
        }, onDismiss = { gradientIndex = null })
    }

    if (confirmDelete) {
        TvDialog("„${scene.name}“ löschen?", onDismiss = { confirmDelete = false }) {
            Text("Zeitpläne, die diese Szene nutzen, bleiben erhalten, tun dann aber nichts mehr.", color = KL.muted, fontSize = 16.sp)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton("Löschen", primary = true, icon = "🗑", onClick = {
                    vm.deleteScene(scene.id)
                    confirmDelete = false
                    onClose()
                })
                TvButton("Behalten", onClick = { confirmDelete = false })
            }
        }
    }

    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
}
