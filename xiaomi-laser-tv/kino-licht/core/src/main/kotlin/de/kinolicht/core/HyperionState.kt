package de.kinolicht.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

data class PriorityInfo(
    val priority: Int,
    val componentId: String,
    val origin: String?,
    val owner: String?,
    val visible: Boolean,
    val color: Rgb?,
)

data class InstanceInfo(val instance: Int, val name: String, val running: Boolean)

/** Ausschnitt der Hyperion-Server-Info, den die App anzeigt. */
data class HyperionState(
    val effects: List<String> = emptyList(),
    val components: Map<String, Boolean> = emptyMap(),
    val brightness: Int? = null,
    val priorities: List<PriorityInfo> = emptyList(),
    val instances: List<InstanceInfo> = emptyList(),
    val ledCount: Int = 0,
    val version: String? = null,
    val hostname: String? = null,
) {
    val ledsOn: Boolean get() = (components["ALL"] ?: true) && (components["LEDDEVICE"] ?: true)

    val visiblePriority: PriorityInfo? get() = priorities.firstOrNull { it.visible } ?: priorities.firstOrNull()

    /** Kurzbeschreibung der aktiven Quelle für die Statuszeile. */
    fun describeSource(ownPriority: Int): String {
        if (!ledsOn) return "LEDs aus"
        val p = visiblePriority ?: return "Keine aktive Quelle"
        val who = if (p.priority == ownPriority) "Kino-Licht" else p.origin?.substringBefore('@')?.ifBlank { null } ?: p.componentId
        val what = when (p.componentId) {
            "COLOR" -> "Farbe ${p.color?.hex ?: ""}".trim()
            "EFFECT" -> "Effekt ${p.owner?.let(Presets::effectLabel) ?: ""}".trim()
            "IMAGE" -> "Farbverlauf"
            "V4L" -> "USB-Aufnahme"
            "GRABBER" -> "Bildschirmaufnahme"
            "FLATBUFSERVER", "PROTOSERVER" -> "Externe Quelle"
            else -> p.componentId
        }
        return "$what · $who"
    }

    companion object {
        fun parse(info: JsonObject): HyperionState {
            val effects = info.array("effects").mapNotNull { it.obj()?.string("name") }
                .distinct()
                .sortedBy { Presets.effectLabel(it).lowercase() }
            val components = info.array("components").mapNotNull { el ->
                val o = el.obj() ?: return@mapNotNull null
                val name = o.string("name") ?: return@mapNotNull null
                name to (o["enabled"]?.jsonPrimitive?.booleanOrNull ?: false)
            }.toMap()
            val brightness = info.array("adjustment").firstOrNull()?.obj()
                ?.get("brightness")?.jsonPrimitive?.intOrNull
            val priorities = info.array("priorities").mapNotNull { el ->
                val o = el.obj() ?: return@mapNotNull null
                val rgb = o["value"]?.obj()?.get("RGB")?.let { it as? JsonArray }
                    ?.mapNotNull { it.jsonPrimitive.intOrNull }
                    ?.takeIf { it.size == 3 && it.all { c -> c in 0..255 } }
                    ?.let { Rgb(it[0], it[1], it[2]) }
                PriorityInfo(
                    priority = o["priority"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null,
                    componentId = o.string("componentId") ?: "",
                    origin = o.string("origin"),
                    owner = o.string("owner"),
                    visible = o["visible"]?.jsonPrimitive?.booleanOrNull ?: false,
                    color = rgb,
                )
            }.sortedBy { it.priority }
            val instances = info.array("instance").mapNotNull { el ->
                val o = el.obj() ?: return@mapNotNull null
                InstanceInfo(
                    instance = o["instance"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null,
                    name = o.string("friendly_name") ?: "Instanz",
                    running = o["running"]?.jsonPrimitive?.booleanOrNull ?: false,
                )
            }
            return HyperionState(
                effects = effects,
                components = components,
                brightness = brightness,
                priorities = priorities,
                instances = instances,
                ledCount = info.array("leds").size,
            )
        }

        private fun JsonObject.array(key: String): List<kotlinx.serialization.json.JsonElement> =
            (this[key] as? JsonArray) ?: emptyList()

        private fun kotlinx.serialization.json.JsonElement.obj(): JsonObject? = this as? JsonObject

        private fun JsonObject.string(key: String): String? =
            runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()
    }
}
