package de.kinolicht.core

import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FrameTest {
    @Test
    fun `RGBA mit Fuellbytes wird zu dichtem RGB`() {
        // 2x2 Pixel, rowStride 12 (4 Fuellbytes), letzte Zeile ohne Fuellbytes wie beim ImageReader
        val data = byteArrayOf(
            1, 2, 3, -1, 4, 5, 6, -1, 99, 99, 99, 99,
            7, 8, 9, -1, 10, 11, 12, -1,
        )
        val rgb = Frame.rgbaToRgb(ByteBuffer.wrap(data), width = 2, height = 2, rowStride = 12)
        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12), rgb)
    }

    @Test
    fun `mittlere Helligkeit`() {
        assertEquals(0, Frame.averageLuma(ByteArray(30)))
        assertEquals(255, Frame.averageLuma(ByteArray(30) { -1 }))
        // reines Gruen wirkt heller als reines Blau
        val green = byteArrayOf(0, -1, 0)
        val blue = byteArrayOf(0, 0, -1)
        assertTrue(Frame.averageLuma(green) > Frame.averageLuma(blue))
    }

    @Test
    fun `Schwarz wird erst nach der Wartezeit gemeldet und zurueckgesetzt`() {
        val d = BlackFrameDetector(thresholdLuma = 4, afterMs = 8000)
        assertFalse(d.update(0, 1_000))
        assertFalse(d.update(2, 8_000))
        assertTrue(d.update(0, 9_000))
        assertFalse(d.update(80, 9_100), "helles Bild setzt zurueck")
        assertFalse(d.update(0, 9_200))
        assertTrue(d.update(0, 17_200))
    }

    @Test
    fun `Bild-Sync liegt direkt hinter der App-Prioritaet`() {
        assertEquals(51, Frame.syncPriority(50))
        val state = HyperionState(
            components = mapOf("ALL" to true, "LEDDEVICE" to true),
            priorities = listOf(PriorityInfo(51, "IMAGE", "Kino-Licht@1.2.3.4", null, true, null)),
        )
        assertEquals("Bild-Sync · Kino-Licht", state.describeSource(ownPriority = 50))
    }
}
