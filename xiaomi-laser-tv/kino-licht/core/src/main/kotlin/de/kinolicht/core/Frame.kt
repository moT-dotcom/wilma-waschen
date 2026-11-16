package de.kinolicht.core

import java.nio.ByteBuffer

/** Hilfen für aufgenommene Bildschirm-Frames (Bild-Sync). */
object Frame {
    /** Priorität des Bild-Sync: direkt hinter den Szenen der App, vor Hyperions eigenen Quellen. */
    fun syncPriority(appPriority: Int): Int = appPriority + 1

    /**
     * Wandelt einen RGBA-Puffer (wie ihn Androids ImageReader liefert) in dichtes RGB um.
     * [rowStride] und [pixelStride] berücksichtigen Füllbytes am Zeilenende.
     */
    fun rgbaToRgb(buffer: ByteBuffer, width: Int, height: Int, rowStride: Int, pixelStride: Int = 4): ByteArray {
        require(pixelStride >= 3) { "pixelStride $pixelStride zu klein" }
        require(rowStride >= width * pixelStride) { "rowStride $rowStride passt nicht zu Breite $width" }
        val out = ByteArray(width * height * 3)
        val row = ByteArray(rowStride)
        var o = 0
        for (y in 0 until height) {
            buffer.position(y * rowStride)
            // Die letzte Zeile ist oft kürzer als rowStride.
            val n = minOf(rowStride, buffer.remaining())
            buffer.get(row, 0, n)
            var i = 0
            for (x in 0 until width) {
                out[o++] = row[i]
                out[o++] = row[i + 1]
                out[o++] = row[i + 2]
                i += pixelStride
            }
        }
        return out
    }

    /** Mittlere Helligkeit 0..255 eines RGB-Bildes. */
    fun averageLuma(rgb: ByteArray): Int {
        if (rgb.size < 3) return 0
        var sum = 0L
        var i = 0
        while (i + 2 < rgb.size) {
            val r = rgb[i].toInt() and 0xFF
            val g = rgb[i + 1].toInt() and 0xFF
            val b = rgb[i + 2].toInt() and 0xFF
            sum += (r * 299 + g * 587 + b * 114) / 1000
            i += 3
        }
        return (sum / (rgb.size / 3)).toInt()
    }
}

/**
 * Erkennt, ob die Aufnahme dauerhaft schwarz bleibt – das Zeichen dafür, dass Android
 * das Bild (HDMI-Eingang, kopiergeschützte Apps) nicht an Aufnahmen herausgibt.
 */
class BlackFrameDetector(private val thresholdLuma: Int = 4, private val afterMs: Long = 8000) {
    private var blackSince: Long? = null

    /** true, sobald seit [afterMs] nur schwarze Bilder kamen. */
    fun update(luma: Int, nowMs: Long): Boolean {
        if (luma > thresholdLuma) {
            blackSince = null
            return false
        }
        val since = blackSince ?: nowMs.also { blackSince = it }
        return nowMs - since >= afterMs
    }
}
