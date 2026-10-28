@file:OptIn(ExperimentalLayoutApi::class)

package de.kinolicht.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.kinolicht.core.Scene
import de.kinolicht.core.Scheduler
import de.kinolicht.core.SunEvent
import de.kinolicht.core.Trigger
import java.time.ZonedDateTime

@Composable
fun AutomationScreen(vm: AppViewModel) {
    val config by vm.config.collectAsStateWithLifecycle()
    val auto = config.automation
    var pick by remember { mutableStateOf<String?>(null) }
    val isNight = Scheduler.isNight(auto, ZonedDateTime.now(), config.location)

    fun sceneLabel(id: String?): String = config.scene(id)?.let { "${it.icon} ${it.name}" } ?: "Letzte Szene"

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 48.dp)) {
        ScreenTitle("Automatik", "Die LEDs denken mit – du musst sie nie extra schalten")

        SectionTitle("Beamer-Kopplung")
        ToggleRow(
            title = "LEDs folgen dem Beamer",
            subtitle = "Beamer an → Licht an · Beamer in Standby → Licht aus",
            checked = auto.followProjector,
            onToggle = { on -> vm.updateAutomation { it.copy(followProjector = on) } },
        )

        if (auto.followProjector) {
            SectionTitle("Beim Einschalten", hint = "Jetzt ist ${if (isNight) "Abend 🌙" else "Tag ☀️"}")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingRow("Tagsüber", sceneLabel(auto.daySceneId), onClick = { pick = "day" }, icon = "☀️")
                SettingRow("Abends", sceneLabel(auto.nightSceneId), onClick = { pick = "night" }, icon = "🌙")
            }
        }

        SectionTitle("Wann ist Abend?", hint = if (config.location == null) "Ohne Standort gilt 19:00 bis 06:00" else null)
        TriggerPicker(
            title = "Abend beginnt",
            trigger = auto.nightStart,
            sunEvent = SunEvent.SUNSET,
            onChange = { t -> vm.updateAutomation { it.copy(nightStart = t) } },
        )
        Spacer(Modifier.height(14.dp))
        TriggerPicker(
            title = "Abend endet",
            trigger = auto.nightEnd,
            sunEvent = SunEvent.SUNRISE,
            onChange = { t -> vm.updateAutomation { it.copy(nightEnd = t) } },
        )

        SectionTitle("Einschlaf-Timer")
        DpadSlider(
            label = "Ausblenden über die letzten",
            value = auto.sleepFadeMinutes,
            range = 1..30,
            onChange = { v -> vm.updateAutomation { it.copy(sleepFadeMinutes = v) } },
            valueText = { "$it min" },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))
        InfoCard(
            "Kino-Licht läuft unauffällig im Hintergrund weiter, auch wenn die App geschlossen ist – nach einem Neustart startet es von selbst.",
            icon = "🤖",
        )
    }

    val target = pick
    if (target != null) {
        val options: List<Scene?> = listOf<Scene?>(null) + config.scenes
        ChoiceDialog(
            title = if (target == "day") "Szene tagsüber" else "Szene abends",
            options = options,
            label = { it?.let { s -> "${s.icon}  ${s.name}" } ?: "↺  Letzte Szene" },
            selected = config.scene(if (target == "day") auto.daySceneId else auto.nightSceneId),
            onPick = { scene ->
                vm.updateAutomation { if (target == "day") it.copy(daySceneId = scene?.id) else it.copy(nightSceneId = scene?.id) }
                pick = null
            },
            onDismiss = { pick = null },
        )
    }
}

@Composable
private fun TriggerPicker(title: String, trigger: Trigger, sunEvent: SunEvent, onChange: (Trigger) -> Unit) {
    Column {
        Text(title, color = KL.muted, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TvChip(sunEvent.label, selected = trigger is Trigger.AtSun, onClick = { onChange(Trigger.AtSun(sunEvent, (trigger as? Trigger.AtSun)?.offsetMinutes ?: 0)) })
            TvChip("Feste Uhrzeit", selected = trigger is Trigger.AtTime, onClick = {
                onChange((trigger as? Trigger.AtTime) ?: if (sunEvent == SunEvent.SUNSET) Trigger.AtTime(19, 0) else Trigger.AtTime(6, 0))
            })
        }
        Spacer(Modifier.height(10.dp))
        when (trigger) {
            is Trigger.AtTime -> TimeSpinner(trigger.hour, trigger.minute, onChange = { h, m -> onChange(Trigger.AtTime(h, m)) })
            is Trigger.AtSun -> DpadSlider(
                label = "Versatz",
                value = trigger.offsetMinutes,
                range = -180..180,
                step = 5,
                onChange = { onChange(trigger.copy(offsetMinutes = it)) },
                valueText = { if (it == 0) "genau" else if (it > 0) "$it min danach" else "${-it} min vorher" },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
