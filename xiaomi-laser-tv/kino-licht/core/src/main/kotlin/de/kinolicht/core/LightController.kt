package de.kinolicht.core

import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/** Szenen, Ein/Aus und sanfte Übergänge auf Basis der Hyperion-Befehle. */
class LightController(
    private val api: HyperionApi,
    private val priority: Int,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {

    /** Instanz und LED-Ausgabe einschalten, falls sie aus sind. */
    suspend fun turnOn() {
        val state = api.serverInfo()
        if (state.components["ALL"] == false) api.setComponent("ALL", true)
        if (state.components["LEDDEVICE"] == false) api.setComponent("LEDDEVICE", true)
    }

    suspend fun turnOff() {
        api.setComponent("LEDDEVICE", false)
    }

    suspend fun apply(action: LightAction) {
        when (action) {
            LightAction.Off -> turnOff()
            LightAction.HyperionDefault -> {
                turnOn()
                api.clear(priority)
            }
            is LightAction.Solid -> {
                turnOn()
                api.setColor(action.color, priority)
            }
            is LightAction.Gradient -> {
                turnOn()
                val image = GradientRenderer.render(action.colors, action.style)
                api.setImage(GradientRenderer.WIDTH, GradientRenderer.HEIGHT, image, priority, "Kino-Licht Verlauf")
            }
            is LightAction.Effect -> {
                turnOn()
                api.setEffect(action.name, priority)
            }
        }
    }

    suspend fun applyScene(scene: Scene) {
        // Helligkeit zuerst, damit die neue Szene nicht kurz zu hell aufblitzt.
        if (scene.brightness != null && scene.action != LightAction.Off) api.setBrightness(scene.brightness)
        apply(scene.action)
    }

    /** Helligkeit linear über [durationMs] ändern; [onStep] meldet den Fortschritt 0..1. */
    suspend fun fadeBrightness(from: Int, to: Int, durationMs: Long, onStep: (Double) -> Unit = {}) {
        val distance = abs(to - from)
        if (distance == 0 || durationMs <= 0) {
            api.setBrightness(to)
            onStep(1.0)
            return
        }
        val steps = distance.coerceAtMost((durationMs / MIN_STEP_MS).toInt()).coerceAtLeast(1)
        val interval = durationMs / steps
        var failures = 0
        for (step in 1..steps) {
            sleep(interval)
            val t = step.toDouble() / steps
            try {
                api.setBrightness((from + (to - from) * t).roundToInt())
                failures = 0
            } catch (e: HyperionException) {
                // Einzelne Aussetzer (WLAN) überspringen, dauerhafte Fehler melden.
                if (++failures >= MAX_FAILURES) throw e
            }
            onStep(t)
        }
    }

    /** Ausblenden, ausschalten und die Helligkeit für das nächste Einschalten zurücksetzen. */
    suspend fun fadeOut(from: Int, durationMs: Long, restoreBrightness: Int, onStep: (Double) -> Unit = {}) {
        fadeBrightness(from, 0, durationMs, onStep)
        turnOff()
        api.setBrightness(restoreBrightness)
    }

    /** Sonnenaufgang: glutrot und dunkel → tageslichtweiß und hell. */
    suspend fun sunrise(durationMs: Long, onStep: (Double) -> Unit = {}) {
        api.setBrightness(100)
        api.setColor(sunriseColor(0.0), priority)
        turnOn()
        val steps = (durationMs / SUNRISE_STEP_MS).toInt().coerceIn(2, 600)
        val interval = durationMs / steps
        var failures = 0
        for (step in 1..steps) {
            sleep(interval)
            val t = step.toDouble() / steps
            try {
                api.setColor(sunriseColor(t), priority)
                failures = 0
            } catch (e: HyperionException) {
                if (++failures >= MAX_FAILURES) throw e
            }
            onStep(t)
        }
    }

    companion object {
        private const val MIN_STEP_MS = 400L
        private const val SUNRISE_STEP_MS = 2000L
        private const val MAX_FAILURES = 5

        fun sunriseColor(t: Double): Rgb {
            val p = t.coerceIn(0.0, 1.0)
            val kelvin = (1000 + 4500 * p.pow(0.8)).roundToInt()
            val intensity = 0.02 + 0.98 * p.pow(1.6)
            return Rgb.fromKelvin(kelvin).scaled(intensity)
        }
    }
}
