package de.kinolicht.core

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Zeitpunkt-Berechnung und lesbare Beschreibungen für Zeitpläne. */
object Scheduler {

    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
    private val dayNames = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

    fun timeOn(trigger: Trigger, date: LocalDate, zone: ZoneId, location: GeoLocation?): ZonedDateTime? = when (trigger) {
        is Trigger.AtTime -> ZonedDateTime.of(date, LocalTime.of(trigger.hour, trigger.minute), zone)
        is Trigger.AtSun -> location?.let { SunCalc.time(trigger.event, date, it, zone) }
            ?.plusMinutes(trigger.offsetMinutes.toLong())
    }

    /** Nächste Ausführung strikt nach [after], oder null (deaktiviert, keine Tage, kein Standort). */
    fun nextRun(rule: ScheduleRule, after: ZonedDateTime, location: GeoLocation?): ZonedDateTime? {
        if (!rule.enabled || rule.days.isEmpty()) return null
        // Ab gestern suchen: Ein Sonnen-Offset kann einen Termin über Mitternacht schieben.
        for (d in -1L..8L) {
            val date = after.toLocalDate().plusDays(d)
            if (date.dayOfWeek.value !in rule.days) continue
            val time = timeOn(rule.trigger, date, after.zone, location) ?: continue
            if (time.isAfter(after)) return time
        }
        return null
    }

    fun isNight(automation: Automation, now: ZonedDateTime, location: GeoLocation?): Boolean {
        val today = now.toLocalDate()
        val start = timeOn(automation.nightStart, today, now.zone, location)
            ?: ZonedDateTime.of(today, LocalTime.of(19, 0), now.zone)
        val end = timeOn(automation.nightEnd, today, now.zone, location)
            ?: ZonedDateTime.of(today, LocalTime.of(6, 0), now.zone)
        return if (start.isAfter(end)) {
            !now.isBefore(start) || now.isBefore(end)
        } else {
            !now.isBefore(start) && now.isBefore(end)
        }
    }

    fun describeTrigger(trigger: Trigger): String = when (trigger) {
        is Trigger.AtTime -> "%02d:%02d".format(trigger.hour, trigger.minute)
        is Trigger.AtSun -> buildString {
            append(trigger.event.label)
            if (trigger.offsetMinutes != 0) {
                append(if (trigger.offsetMinutes > 0) " +" else " −")
                append(kotlin.math.abs(trigger.offsetMinutes))
                append(" min")
            }
        }
    }

    fun describeDays(days: Set<Int>): String = when (days) {
        ScheduleRule.ALL_DAYS -> "Täglich"
        ScheduleRule.WORKDAYS -> "Mo–Fr"
        ScheduleRule.WEEKEND -> "Sa + So"
        emptySet<Int>() -> "Nie"
        else -> days.sorted().joinToString(", ") { dayNames[it - 1] }
    }

    fun describeAction(action: RuleAction, scenes: List<Scene>): String = when (action) {
        is RuleAction.ApplyScene -> scenes.firstOrNull { it.id == action.sceneId }
            ?.let { "${it.icon} ${it.name}" } ?: "Gelöschte Szene"
        RuleAction.TurnOn -> "Einschalten"
        RuleAction.TurnOff -> "Ausschalten"
        is RuleAction.FadeOut -> "Ausblenden in ${action.minutes} min"
        is RuleAction.Sunrise -> "Sonnenaufgang über ${action.minutes} min"
        is RuleAction.FadeBrightness -> "Helligkeit auf ${action.target} % in ${action.minutes} min"
    }

    fun formatTime(time: ZonedDateTime, now: ZonedDateTime): String {
        val days = java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), time.toLocalDate())
        val day = when (days) {
            0L -> "heute"
            1L -> "morgen"
            else -> dayNames[time.dayOfWeek.value - 1]
        }
        return "$day ${time.format(timeFormat)}"
    }
}
