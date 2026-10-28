package de.kinolicht.core

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

@Serializable
data class Rgb(val r: Int, val g: Int, val b: Int) {

    init {
        require(r in 0..255 && g in 0..255 && b in 0..255) { "RGB außerhalb 0..255: $r,$g,$b" }
    }

    val hex: String get() = "#%02X%02X%02X".format(r, g, b)

    /** 0xAARRGGBB für Compose/Android. */
    val argb: Long get() = 0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()

    fun scaled(factor: Double): Rgb = Rgb(
        (r * factor).roundToInt().coerceIn(0, 255),
        (g * factor).roundToInt().coerceIn(0, 255),
        (b * factor).roundToInt().coerceIn(0, 255),
    )

    fun lerp(other: Rgb, t: Double): Rgb {
        val f = t.coerceIn(0.0, 1.0)
        return Rgb(
            (r + (other.r - r) * f).roundToInt(),
            (g + (other.g - g) * f).roundToInt(),
            (b + (other.b - b) * f).roundToInt(),
        )
    }

    /** Hue 0..360, Sättigung und Helligkeit 0..1. */
    fun toHsv(): Hsv {
        val rf = r / 255.0
        val gf = g / 255.0
        val bf = b / 255.0
        val max = maxOf(rf, gf, bf)
        val min = minOf(rf, gf, bf)
        val delta = max - min
        val hue = when {
            delta == 0.0 -> 0.0
            max == rf -> 60 * (((gf - bf) / delta).mod(6.0))
            max == gf -> 60 * ((bf - rf) / delta + 2)
            else -> 60 * ((rf - gf) / delta + 4)
        }
        val sat = if (max == 0.0) 0.0 else delta / max
        return Hsv(hue, sat, max)
    }

    companion object {
        val BLACK = Rgb(0, 0, 0)
        val WHITE = Rgb(255, 255, 255)

        fun fromHex(hex: String): Rgb {
            val clean = hex.removePrefix("#")
            require(clean.length == 6) { "Ungültige Farbe: $hex" }
            val v = clean.toInt(16)
            return Rgb((v shr 16) and 0xFF, (v shr 8) and 0xFF, v and 0xFF)
        }

        fun fromHsv(hue: Double, saturation: Double, value: Double): Rgb {
            val h = hue.mod(360.0)
            val s = saturation.coerceIn(0.0, 1.0)
            val v = value.coerceIn(0.0, 1.0)
            val c = v * s
            val x = c * (1 - abs((h / 60).mod(2.0) - 1))
            val m = v - c
            val (rf, gf, bf) = when {
                h < 60 -> Triple(c, x, 0.0)
                h < 120 -> Triple(x, c, 0.0)
                h < 180 -> Triple(0.0, c, x)
                h < 240 -> Triple(0.0, x, c)
                h < 300 -> Triple(x, 0.0, c)
                else -> Triple(c, 0.0, x)
            }
            return Rgb(
                ((rf + m) * 255).roundToInt().coerceIn(0, 255),
                ((gf + m) * 255).roundToInt().coerceIn(0, 255),
                ((bf + m) * 255).roundToInt().coerceIn(0, 255),
            )
        }

        /**
         * Farbtemperatur in Kelvin (1000–40000) nach RGB.
         * Näherung nach Tanner Helland, genau genug für LED-Licht.
         */
        fun fromKelvin(kelvin: Int): Rgb {
            val t = kelvin.coerceIn(1000, 40000) / 100.0
            val red = if (t <= 66) 255.0 else 329.698727446 * (t - 60).pow(-0.1332047592)
            val green = if (t <= 66) 99.4708025861 * ln(t) - 161.1195681661
            else 288.1221695283 * (t - 60).pow(-0.0755148492)
            val blue = when {
                t >= 66 -> 255.0
                t <= 19 -> 0.0
                else -> 138.5177312231 * ln(t - 10) - 305.0447927307
            }
            return Rgb(
                red.roundToInt().coerceIn(0, 255),
                green.roundToInt().coerceIn(0, 255),
                blue.roundToInt().coerceIn(0, 255),
            )
        }
    }
}

data class Hsv(val hue: Double, val saturation: Double, val value: Double) {
    fun toRgb(): Rgb = Rgb.fromHsv(hue, saturation, value)
}
