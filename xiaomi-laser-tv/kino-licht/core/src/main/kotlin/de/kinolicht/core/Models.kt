package de.kinolicht.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString().substring(0, 8)

@Serializable
enum class GradientStyle(val label: String) {
    AROUND("Rundherum"),
    HORIZONTAL("Links → Rechts"),
    VERTICAL("Oben → Unten"),
    MIRROR("Gespiegelt"),
}

/** Was die LEDs zeigen sollen. */
@Serializable
sealed interface LightAction {
    @Serializable
    @SerialName("solid")
    data class Solid(val color: Rgb) : LightAction

    @Serializable
    @SerialName("gradient")
    data class Gradient(val colors: List<Rgb>, val style: GradientStyle = GradientStyle.AROUND) : LightAction

    @Serializable
    @SerialName("effect")
    data class Effect(val name: String) : LightAction

    /** Eigene Priorität freigeben – Hyperion zeigt wieder seine Standardquelle. */
    @Serializable
    @SerialName("hyperion")
    data object HyperionDefault : LightAction

    @Serializable
    @SerialName("off")
    data object Off : LightAction
}

@Serializable
data class Scene(
    val id: String = newId(),
    val name: String,
    val icon: String,
    val action: LightAction,
    /** 0..100, null = Helligkeit nicht verändern. */
    val brightness: Int? = null,
)

@Serializable
enum class SunEvent(val label: String) { SUNRISE("Sonnenaufgang"), SUNSET("Sonnenuntergang") }

@Serializable
sealed interface Trigger {
    @Serializable
    @SerialName("time")
    data class AtTime(val hour: Int, val minute: Int) : Trigger

    @Serializable
    @SerialName("sun")
    data class AtSun(val event: SunEvent, val offsetMinutes: Int = 0) : Trigger
}

@Serializable
sealed interface RuleAction {
    @Serializable
    @SerialName("scene")
    data class ApplyScene(val sceneId: String) : RuleAction

    @Serializable
    @SerialName("on")
    data object TurnOn : RuleAction

    @Serializable
    @SerialName("off")
    data object TurnOff : RuleAction

    /** Sanft ausblenden und ausschalten. */
    @Serializable
    @SerialName("fadeOut")
    data class FadeOut(val minutes: Int) : RuleAction

    /** Sonnenaufgang simulieren: von glutrot zu tageslichtweiß. */
    @Serializable
    @SerialName("sunrise")
    data class Sunrise(val minutes: Int) : RuleAction

    @Serializable
    @SerialName("brightness")
    data class FadeBrightness(val target: Int, val minutes: Int) : RuleAction
}

@Serializable
data class ScheduleRule(
    val id: String = newId(),
    val enabled: Boolean = true,
    val trigger: Trigger,
    /** ISO-Wochentage: 1 = Montag … 7 = Sonntag. */
    val days: Set<Int> = ALL_DAYS,
    val action: RuleAction,
    /** Nur ausführen, wenn der Beamer gerade eingeschaltet ist. */
    val onlyWhenProjectorOn: Boolean = false,
) {
    companion object {
        val ALL_DAYS = (1..7).toSet()
        val WORKDAYS = (1..5).toSet()
        val WEEKEND = setOf(6, 7)
    }
}

@Serializable
data class Automation(
    /** LEDs gehen mit dem Beamer an und aus. */
    val followProjector: Boolean = true,
    /** Szene beim Einschalten tagsüber, null = zuletzt genutzte Szene. */
    val daySceneId: String? = null,
    /** Szene beim Einschalten abends/nachts, null = zuletzt genutzte Szene. */
    val nightSceneId: String? = null,
    val nightStart: Trigger = Trigger.AtSun(SunEvent.SUNSET, -30),
    val nightEnd: Trigger = Trigger.AtTime(6, 0),
    /** Ausblenddauer des Einschlaf-Timers. */
    val sleepFadeMinutes: Int = 5,
)

@Serializable
data class ConnectionSettings(
    val host: String = "",
    val port: Int = HyperionClient.DEFAULT_PORT,
    val token: String? = null,
    /** Hyperion-Priorität dieser App, kleiner = wichtiger. Grabber liegen bei 240+. */
    val priority: Int = 50,
    val instance: Int = 0,
) {
    val isConfigured: Boolean get() = host.isNotBlank()
}

@Serializable
data class GeoLocation(val name: String, val latitude: Double, val longitude: Double)

@Serializable
data class AppConfig(
    val connection: ConnectionSettings = ConnectionSettings(),
    val location: GeoLocation? = null,
    val scenes: List<Scene> = Presets.defaultScenes,
    val favoriteColors: List<Rgb> = emptyList(),
    val favoriteEffects: List<String> = emptyList(),
    val rules: List<ScheduleRule> = emptyList(),
    val automation: Automation = Automation(),
    /** Zuletzt aktive Szene (auch spontan gewählte Farben), für „Letzte Szene“ beim Einschalten. */
    val lastScene: Scene? = null,
    /** Zuletzt gesetzte Helligkeit, um sie nach dem Ausblenden wiederherzustellen. */
    val userBrightness: Int = 100,
) {
    fun scene(id: String?): Scene? = scenes.firstOrNull { it.id == id }

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            classDiscriminator = "type"
        }

        /** Liest gespeicherte Konfiguration; bei Fehlern lieber Standard als Absturz. */
        fun fromJson(text: String?): AppConfig =
            if (text.isNullOrBlank()) AppConfig()
            else runCatching { json.decodeFromString(serializer(), text) }.getOrElse { AppConfig() }
    }
}
