@file:OptIn(ExperimentalLayoutApi::class)

package de.kinolicht.app.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.kinolicht.app.AlarmScheduler
import de.kinolicht.core.AppConfig
import de.kinolicht.core.RuleAction
import de.kinolicht.core.ScheduleRule
import de.kinolicht.core.Scheduler
import de.kinolicht.core.SunEvent
import de.kinolicht.core.Trigger
import java.time.ZonedDateTime

private data class RuleTemplate(val icon: String, val title: String, val rule: (AppConfig) -> ScheduleRule)

private val templates = listOf(
    RuleTemplate("🌇", "Abends automatisch an") { cfg ->
        ScheduleRule(
            trigger = Trigger.AtSun(SunEvent.SUNSET, -20),
            action = RuleAction.ApplyScene(cfg.scenes.firstOrNull { it.id == "abend" }?.id ?: cfg.scenes.first().id),
            onlyWhenProjectorOn = true,
        )
    },
    RuleTemplate("😴", "23:30 sanft ausblenden") {
        ScheduleRule(trigger = Trigger.AtTime(23, 30), action = RuleAction.FadeOut(10))
    },
    RuleTemplate("☀️", "Lichtwecker werktags 6:45") {
        ScheduleRule(trigger = Trigger.AtTime(6, 45), days = ScheduleRule.WORKDAYS, action = RuleAction.Sunrise(20))
    },
    RuleTemplate("🌙", "22 Uhr gedimmt") {
        ScheduleRule(trigger = Trigger.AtTime(22, 0), action = RuleAction.FadeBrightness(25, 15), onlyWhenProjectorOn = true)
    },
)

@Composable
fun ScheduleScreen(vm: AppViewModel) {
    var editing by remember { mutableStateOf<ScheduleRule?>(null) }
    val config by vm.config.collectAsStateWithLifecycle()
    val current = editing
    if (current != null) {
        RuleEditor(vm, current, isNew = config.rules.none { it.id == current.id }, onClose = { editing = null })
    } else {
        ScheduleOverview(vm, onEdit = { editing = it })
    }
}

@Composable
private fun ScheduleOverview(vm: AppViewModel, onEdit: (ScheduleRule) -> Unit) {
    val config by vm.config.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val now = remember { ZonedDateTime.now() }
    val needsLocation = config.location == null && config.rules.any { it.trigger is Trigger.AtSun }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 48.dp)) {
        ScreenTitle("Zeitplan", "Licht nach Uhrzeit oder Sonnenstand – läuft auch ohne geöffnete App")

        if (needsLocation) {
            Spacer(Modifier.height(14.dp))
            InfoCard("Für Sonnenauf- und -untergang bitte unter „Verbindung“ deinen Standort wählen.", icon = "📍")
        }
        if (!AlarmScheduler.canScheduleExact(context)) {
            Spacer(Modifier.height(14.dp))
            InfoCard("Android erlaubt gerade keine minutengenauen Wecker – Zeitpläne können sich um einige Minuten verschieben.", icon = "⚠️")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Spacer(Modifier.height(8.dp))
                TvButton("Genaue Wecker erlauben", icon = "⏰", onClick = {
                    runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)) }
                })
            }
        }

        SectionTitle("Meine Zeitpläne")
        if (config.rules.isEmpty()) {
            Text("Noch keine Zeitpläne – starte mit einer Vorlage unten oder lege einen eigenen an.", color = KL.muted, fontSize = 16.sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            config.rules.forEach { rule ->
                val next = Scheduler.nextRun(rule, now, config.location)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    FocusBox(
                        onClick = { onEdit(rule) },
                        modifier = Modifier.weight(1f).height(92.dp),
                        focusScale = 1.02f,
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Row(Modifier.padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                Scheduler.describeTrigger(rule.trigger),
                                color = if (rule.enabled) KL.text else KL.muted,
                                fontSize = if (rule.trigger is Trigger.AtTime) 32.sp else 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(250.dp),
                                maxLines = 2,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    Scheduler.describeAction(rule.action, config.scenes),
                                    color = if (rule.enabled) KL.text else KL.muted,
                                    fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    buildString {
                                        append(Scheduler.describeDays(rule.days))
                                        if (rule.onlyWhenProjectorOn) append(" · nur bei Beamer an")
                                        append(" · ")
                                        append(
                                            when {
                                                !rule.enabled -> "pausiert"
                                                next != null -> "nächstes Mal ${Scheduler.formatTime(next, now)}"
                                                else -> "kein Termin (Standort?)"
                                            },
                                        )
                                    },
                                    color = KL.muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text("✎", color = KL.muted, fontSize = 20.sp)
                        }
                    }
                    FocusBox(
                        onClick = { vm.setRuleEnabled(rule.id, !rule.enabled) },
                        modifier = Modifier.size(width = 110.dp, height = 92.dp),
                        selected = rule.enabled,
                    ) {
                        Text(if (rule.enabled) "Aktiv" else "Pause", color = if (rule.enabled) KL.accent else KL.muted, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        TvButton("Neuer Zeitplan", primary = true, icon = "+", onClick = {
            onEdit(ScheduleRule(trigger = Trigger.AtTime(20, 0), action = RuleAction.ApplyScene(config.scenes.first().id)))
        })

        SectionTitle("Vorlagen", hint = "Ein Klick legt den Zeitplan an – danach kannst du ihn anpassen")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            templates.forEach { t ->
                FocusBox(
                    onClick = { onEdit(t.rule(config)) },
                    modifier = Modifier.size(width = 240.dp, height = 104.dp),
                    shape = RoundedCornerShape(20.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Column(Modifier.padding(horizontal = 18.dp)) {
                        Text(t.icon, fontSize = 28.sp)
                        Text(t.title, color = KL.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

private enum class TriggerType(val label: String) { TIME("Uhrzeit"), SUNRISE("Sonnenaufgang"), SUNSET("Sonnenuntergang") }
private enum class ActionType(val label: String) { SCENE("Szene"), ON("Einschalten"), OFF("Ausschalten"), FADE("Ausblenden"), SUNRISE("Lichtwecker"), DIM("Helligkeit") }

private fun RuleAction.type() = when (this) {
    is RuleAction.ApplyScene -> ActionType.SCENE
    RuleAction.TurnOn -> ActionType.ON
    RuleAction.TurnOff -> ActionType.OFF
    is RuleAction.FadeOut -> ActionType.FADE
    is RuleAction.Sunrise -> ActionType.SUNRISE
    is RuleAction.FadeBrightness -> ActionType.DIM
}

@Composable
private fun RuleEditor(vm: AppViewModel, initial: ScheduleRule, isNew: Boolean, onClose: () -> Unit) {
    val config by vm.config.collectAsStateWithLifecycle()
    var rule by remember { mutableStateOf(initial) }
    var pickScene by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    BackHandler(onBack = onClose)

    val triggerType = when (val t = rule.trigger) {
        is Trigger.AtTime -> TriggerType.TIME
        is Trigger.AtSun -> if (t.event == SunEvent.SUNRISE) TriggerType.SUNRISE else TriggerType.SUNSET
    }
    val next = Scheduler.nextRun(rule.copy(enabled = true), ZonedDateTime.now(), config.location)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 48.dp)) {
        ScreenTitle(if (isNew) "Neuer Zeitplan" else "Zeitplan bearbeiten", "Zurück-Taste verwirft Änderungen")

        SectionTitle("Wann?")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TriggerType.entries.forEach { t ->
                TvChip(t.label, selected = t == triggerType, onClick = {
                    rule = rule.copy(
                        trigger = when (t) {
                            TriggerType.TIME -> (rule.trigger as? Trigger.AtTime) ?: Trigger.AtTime(20, 0)
                            TriggerType.SUNRISE -> Trigger.AtSun(SunEvent.SUNRISE, (rule.trigger as? Trigger.AtSun)?.offsetMinutes ?: 0)
                            TriggerType.SUNSET -> Trigger.AtSun(SunEvent.SUNSET, (rule.trigger as? Trigger.AtSun)?.offsetMinutes ?: 0)
                        },
                    )
                })
            }
        }
        Spacer(Modifier.height(14.dp))
        when (val t = rule.trigger) {
            is Trigger.AtTime -> TimeSpinner(t.hour, t.minute, onChange = { h, m -> rule = rule.copy(trigger = Trigger.AtTime(h, m)) })
            is Trigger.AtSun -> DpadSlider(
                label = "Versatz zum ${t.event.label}",
                value = t.offsetMinutes,
                range = -180..180,
                step = 5,
                onChange = { rule = rule.copy(trigger = t.copy(offsetMinutes = it)) },
                valueText = { if (it == 0) "genau" else if (it > 0) "$it min danach" else "${-it} min vorher" },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionTitle("An welchen Tagen?")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TvChip("Täglich", selected = rule.days == ScheduleRule.ALL_DAYS, onClick = { rule = rule.copy(days = ScheduleRule.ALL_DAYS) })
            TvChip("Mo–Fr", selected = rule.days == ScheduleRule.WORKDAYS, onClick = { rule = rule.copy(days = ScheduleRule.WORKDAYS) })
            TvChip("Wochenende", selected = rule.days == ScheduleRule.WEEKEND, onClick = { rule = rule.copy(days = ScheduleRule.WEEKEND) })
            Spacer(Modifier.width(18.dp))
            listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEachIndexed { i, name ->
                val day = i + 1
                TvChip(name, selected = day in rule.days, onClick = {
                    rule = rule.copy(days = if (day in rule.days) rule.days - day else rule.days + day)
                })
            }
        }

        SectionTitle("Was passiert?")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionType.entries.forEach { a ->
                TvChip(a.label, selected = rule.action.type() == a, onClick = {
                    rule = rule.copy(
                        action = when (a) {
                            ActionType.SCENE -> RuleAction.ApplyScene((rule.action as? RuleAction.ApplyScene)?.sceneId ?: config.scenes.first().id)
                            ActionType.ON -> RuleAction.TurnOn
                            ActionType.OFF -> RuleAction.TurnOff
                            ActionType.FADE -> RuleAction.FadeOut(10)
                            ActionType.SUNRISE -> RuleAction.Sunrise(20)
                            ActionType.DIM -> RuleAction.FadeBrightness(30, 10)
                        },
                    )
                })
            }
        }
        Spacer(Modifier.height(14.dp))
        when (val a = rule.action) {
            is RuleAction.ApplyScene -> SettingRow("Szene", Scheduler.describeAction(a, config.scenes), onClick = { pickScene = true }, icon = "🎬")
            RuleAction.TurnOn -> InfoCard("Schaltet die LEDs ein – mit dem, was zuletzt lief.", icon = "⏻")
            RuleAction.TurnOff -> InfoCard("Schaltet die LEDs sofort aus.", icon = "⏻")
            is RuleAction.FadeOut -> DpadSlider("Ausblenden über", a.minutes, 1..60, onChange = { rule = rule.copy(action = RuleAction.FadeOut(it)) }, valueText = { "$it min" }, modifier = Modifier.fillMaxWidth())
            is RuleAction.Sunrise -> Column {
                InfoCard("Simuliert einen Sonnenaufgang: von glutrotem Glimmen zu hellem Tageslicht.", icon = "🌅")
                Spacer(Modifier.height(10.dp))
                DpadSlider("Dauer", a.minutes, 5..60, onChange = { rule = rule.copy(action = RuleAction.Sunrise(it)) }, valueText = { "$it min" }, modifier = Modifier.fillMaxWidth())
            }
            is RuleAction.FadeBrightness -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DpadSlider("Ziel-Helligkeit", a.target, 0..100, step = 5, onChange = { rule = rule.copy(action = a.copy(target = it)) }, valueText = { "$it %" }, modifier = Modifier.fillMaxWidth())
                DpadSlider("Übergang", a.minutes, 0..60, onChange = { rule = rule.copy(action = a.copy(minutes = it)) }, valueText = { if (it == 0) "sofort" else "$it min" }, modifier = Modifier.fillMaxWidth())
            }
        }

        SectionTitle("Bedingung")
        ToggleRow(
            title = "Nur wenn der Beamer läuft",
            subtitle = "Sonst wird dieser Termin übersprungen",
            checked = rule.onlyWhenProjectorOn,
            onToggle = { rule = rule.copy(onlyWhenProjectorOn = it) },
        )

        Spacer(Modifier.height(18.dp))
        Text(
            when {
                rule.days.isEmpty() -> "⚠ Kein Tag ausgewählt"
                next == null -> "⚠ Kein Termin berechenbar – Standort unter „Verbindung“ wählen"
                else -> "Nächste Ausführung: ${Scheduler.formatTime(next, ZonedDateTime.now())}"
            },
            color = if (next == null) KL.bad else KL.muted,
            fontSize = 16.sp,
        )
        Spacer(Modifier.height(18.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton("Speichern", primary = true, icon = "💾", onClick = {
                vm.saveRule(rule)
                onClose()
            })
            TvButton("Jetzt testen", icon = "▶", onClick = { vm.testRule(rule) })
            if (!isNew) TvButton("Löschen", icon = "🗑", onClick = { confirmDelete = true })
            TvButton("Abbrechen", onClick = onClose)
        }
    }

    if (pickScene) {
        val selected = (rule.action as? RuleAction.ApplyScene)?.sceneId
        ChoiceDialog(
            title = "Szene wählen",
            options = config.scenes,
            label = { "${it.icon}  ${it.name}" },
            selected = config.scenes.firstOrNull { it.id == selected },
            onPick = {
                rule = rule.copy(action = RuleAction.ApplyScene(it.id))
                pickScene = false
            },
            onDismiss = { pickScene = false },
        )
    }

    if (confirmDelete) {
        TvDialog("Zeitplan löschen?", onDismiss = { confirmDelete = false }) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton("Löschen", primary = true, icon = "🗑", onClick = {
                    vm.deleteRule(rule.id)
                    confirmDelete = false
                    onClose()
                })
                TvButton("Behalten", onClick = { confirmDelete = false })
            }
        }
    }
}
