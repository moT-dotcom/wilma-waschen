package de.kinolicht.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor

/**
 * Rendert Farbverläufe als kleines RGB-Bild. Hyperion bildet die Bildränder
 * auf die LEDs ab – so entstehen mehrfarbige Szenen ohne eigenes Effekt-Skript.
 */
object GradientRenderer {
    const val WIDTH = 64
    const val HEIGHT = 36

    fun render(colors: List<Rgb>, style: GradientStyle, width: Int = WIDTH, height: Int = HEIGHT): ByteArray {
        require(colors.isNotEmpty()) { "Mindestens eine Farbe nötig" }
        val out = ByteArray(width * height * 3)
        var i = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val c = colorAt(colors, style, (x + 0.5) / width, (y + 0.5) / height)
                out[i++] = c.r.toByte()
                out[i++] = c.g.toByte()
                out[i++] = c.b.toByte()
            }
        }
        return out
    }

    /** Farbe an Position x/y (jeweils 0..1). Auch für die Vorschau in der App. */
    fun colorAt(colors: List<Rgb>, style: GradientStyle, x: Double, y: Double): Rgb = when (style) {
        GradientStyle.HORIZONTAL -> sample(colors, x)
        GradientStyle.VERTICAL -> sample(colors, y)
        GradientStyle.MIRROR -> sample(colors, 1 - abs(2 * x - 1))
        GradientStyle.AROUND -> {
            // Winkel um die Bildmitte, im 16:9-Verhältnis, damit der Verlauf gleichmäßig um den Rahmen läuft.
            val angle = atan2((y - 0.5) * 9, (x - 0.5) * 16)
            sample(colors + colors.first(), (angle + PI) / (2 * PI))
        }
    }

    private fun sample(stops: List<Rgb>, t: Double): Rgb {
        if (stops.size == 1) return stops[0]
        val pos = t.coerceIn(0.0, 1.0) * (stops.size - 1)
        val i = floor(pos).toInt().coerceAtMost(stops.size - 2)
        return stops[i].lerp(stops[i + 1], pos - i)
    }
}
