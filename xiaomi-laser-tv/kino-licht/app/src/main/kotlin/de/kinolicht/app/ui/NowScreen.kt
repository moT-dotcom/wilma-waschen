@file:OptIn(ExperimentalLayoutApi::class)

package de.kinolicht.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.kinolicht.core.LightAction
import de.kinolicht.core.Scene
import java.time.LocalTime

@Composable
fun NowScreen(vm: AppViewModel, onOpenSettings: () -> Unit) {
    val config by vm.config.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val sleepEndsAt by vm.sleepEndsAt.collectAsStateWithLifecycle()
    val ledsOn = state?.ledsOn ?: true
    val brightness = state?.brightness ?: config.userBrightness

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 48.dp)) {
        ScreenTitle(greeting(), "Was soll leuchten?")

        if (status == ConnectionStatus.NotConfigured) {
            Spacer(Modifier.height(16.dp))
            InfoCard("Willkommen! Verbinde Kino-Licht einmalig mit deinem Hyperion auf dem Raspberry Pi.", icon = "👋")
            Spacer(Modifier.height(12.dp))
            TvButton("Jetzt einrichten", onClick = onOpenSettings, primary = true, icon = "🔌")
        } else {
            NowContent(vm, config, ledsOn, brightness, sleepEndsAt)
        }
    }
}

@Composable
private fun NowContent(
    vm: AppViewModel,
    config: de.kinolicht.core.AppConfig,
    ledsOn: Boolean,
    brightness: Int,
    sleepEndsAt: Long?,
) {
    Column {
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            FocusBox(
                onClick = { vm.setPower(!ledsOn) },
                modifier = Modifier.size(width = 230.dp, height = 104.dp),
                background = if (ledsOn) KL.selected else KL.surface,
                shape = RoundedCornerShape(22.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⏻", fontSize = 34.sp, color = if (ledsOn) KL.accent else KL.muted)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(if (ledsOn) "LEDs an" else "LEDs aus", color = KL.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(if (ledsOn) "OK zum Ausschalten" else "OK zum Einschalten", color = KL.muted, fontSize = 14.sp)
                    }
                }
            }
            DpadSlider(
                label = "☀ Helligkeit",
                value = brightness,
                range = 0..100,
                step = 5,
                onChange = vm::setBrightness,
                valueText = { "$it %" },
                modifier = Modifier.weight(1f),
            )
        }

        SectionTitle("Szenen", hint = "Ein Klick – und der Raum passt zum Film")
        SceneGrid(config.scenes, current = config.lastScene, onClick = vm::applyScene)

        SectionTitle("Einschlaf-Timer", hint = "Blendet die LEDs sanft aus – auch wenn du schon schläfst")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(15, 30, 45, 60, 90, 120).forEach { minutes ->
                TvChip("$minutes min", selected = false, onClick = { vm.startSleepTimer(minutes) })
            }
            if (sleepEndsAt != null) TvChip("✕ Timer stoppen", selected = true, onClick = vm::cancelTimer)
        }

        SectionTitle("Schnellzugriff")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton("Zurück zu Hyperion", onClick = vm::backToHyperion, icon = "↩")
            config.lastScene?.let { last ->
                TvButton("Letzte: ${last.name}", onClick = { vm.applyScene(last) }, icon = last.icon)
            }
            TvButton("Aktualisieren", onClick = vm::refresh, icon = "⟳")
        }
    }
}

@Composable
fun SceneGrid(scenes: List<Scene>, current: Scene?, onClick: (Scene) -> Unit, trailing: (@Composable () -> Unit)? = null) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        scenes.forEach { scene ->
            SceneCard(scene, active = current?.id == scene.id, onClick = { onClick(scene) })
        }
        trailing?.invoke()
    }
}

@Composable
fun SceneCard(scene: Scene, active: Boolean, onClick: () -> Unit) {
    FocusBox(
        onClick = onClick,
        modifier = Modifier.size(width = 190.dp, height = 132.dp),
        selected = active,
        shape = RoundedCornerShape(20.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(Modifier.fillMaxSize()) {
            ActionPreview(scene.action, Modifier.fillMaxWidth().height(10.dp))
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(scene.icon, fontSize = 30.sp)
                Spacer(Modifier.height(6.dp))
                Text(scene.name, color = KL.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sceneSubtitle(scene), color = KL.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

fun sceneSubtitle(scene: Scene): String {
    val what = when (val a = scene.action) {
        is LightAction.Solid -> "Farbe"
        is LightAction.Gradient -> "Verlauf · ${a.style.label}"
        is LightAction.Effect -> de.kinolicht.core.Presets.effectLabel(a.name)
        LightAction.HyperionDefault -> "Hyperion-Standard"
        LightAction.Off -> "LEDs aus"
    }
    return scene.brightness?.let { "$what · $it %" } ?: what
}

@Composable
fun AddCard(label: String, onClick: () -> Unit) {
    FocusBox(
        onClick = onClick,
        modifier = Modifier.size(width = 190.dp, height = 132.dp),
        shape = RoundedCornerShape(20.dp),
        background = Color(0xFF12141A),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.clip(RoundedCornerShape(20.dp)).background(KL.surface2).padding(horizontal = 14.dp, vertical = 2.dp)) {
                Text("+", color = KL.accent, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text(label, color = KL.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..10 -> "Guten Morgen"
    in 11..16 -> "Hallo"
    in 17..21 -> "Guten Abend"
    else -> "Gute Nacht"
}
