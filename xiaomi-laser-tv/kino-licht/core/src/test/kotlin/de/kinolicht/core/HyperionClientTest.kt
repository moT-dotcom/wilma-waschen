package de.kinolicht.core

import de.kinolicht.core.FakeHyperionServer.Companion.str
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HyperionClientTest {
    private val server = FakeHyperionServer()
    private val client = HyperionClient("127.0.0.1", server.port)

    @AfterTest
    fun tearDown() {
        client.close()
        server.close()
    }

    @Test
    fun `setzt Farbe mit Prioritaet und Herkunft`() = runBlocking {
        client.setColor(Rgb(255, 128, 0), priority = 50)
        val req = server.requests.single()
        assertEquals("color", req.str("command"))
        assertEquals(listOf(255, 128, 0), req["color"]!!.jsonArray.map { it.jsonPrimitive.int })
        assertEquals(50, req["priority"]!!.jsonPrimitive.int)
        assertEquals("Kino-Licht", req.str("origin"))
    }

    @Test
    fun `liest Server-Info trotz vorangestelltem Abo-Update`() = runBlocking {
        val state = client.fetchState()
        assertEquals(listOf("Kerze", "Mein Effekt", "Regenbogen-Wirbel"), state.effects.map(Presets::effectLabel))
        assertEquals(80, state.brightness)
        assertFalse(state.ledsOn, "LEDDEVICE ist im Fixture aus")
        assertEquals(Rgb(255, 120, 0), state.visiblePriority?.color)
        assertEquals("Wohnzimmer", state.instances.single().name)
        assertEquals(4, state.ledCount)
        assertEquals("2.0.16", state.version)
        assertEquals("pi", state.hostname)
    }

    @Test
    fun `beschreibt aktive Quelle`() = runBlocking {
        val state = client.serverInfo().copy(components = mapOf("ALL" to true, "LEDDEVICE" to true))
        assertEquals("Farbe #FF7800 · Kino-Licht", state.describeSource(ownPriority = 50))
    }

    @Test
    fun `sendet Komponenten, Helligkeit, Effekt, Clear und Bild`() = runBlocking {
        client.setComponent("LEDDEVICE", true)
        client.setBrightness(140)
        client.setEffect("Candle", 50)
        client.clear(50)
        client.setImage(2, 1, byteArrayOf(1, 2, 3, 4, 5, 6), 50, "Verlauf")

        val (component, adjustment, effect, clear, image) = server.requests
        assertEquals("LEDDEVICE", component["componentstate"]!!.jsonObject.str("component"))
        assertEquals("true", component["componentstate"]!!.jsonObject.str("state"))
        assertEquals(100, adjustment["adjustment"]!!.jsonObject["brightness"]!!.jsonPrimitive.int, "auf 100 begrenzt")
        assertEquals("Candle", effect["effect"]!!.jsonObject.str("name"))
        assertEquals("clear", clear.str("command"))
        assertEquals("AQIDBAUG", image.str("imagedata"))
        assertEquals(2, image["imagewidth"]!!.jsonPrimitive.int)
        assertEquals(1, image["imageheight"]!!.jsonPrimitive.int)
    }

    @Test
    fun `meldet Hyperion-Fehler als Exception`() = runBlocking {
        val e = assertFailsWith<HyperionException> { client.setEffect("Gibt es nicht", 50) }
        assertEquals("Effect not found", e.message)
        // Die Verbindung bleibt danach nutzbar.
        client.clear(50)
    }

    @Test
    fun `baut nach Verbindungsabbruch automatisch neu auf`() = runBlocking {
        client.clear(50)
        server.dropConnections()
        Thread.sleep(50)
        client.clear(50)
        assertEquals(2, server.requests.count { it.str("command") == "clear" })
    }

    @Test
    fun `nicht erreichbarer Server liefert verstaendliche Meldung`() = runBlocking {
        val dead = HyperionClient("127.0.0.1", port = 1, connectTimeoutMs = 500)
        val e = assertFailsWith<HyperionException> { dead.clear(50) }
        assertTrue(e.message!!.contains("nicht erreichbar"), e.message)
    }

    @Test
    fun `meldet sich mit Token an`() = runBlocking {
        FakeHyperionServer(requiredToken = "geheim").use { secured ->
            HyperionClient("127.0.0.1", secured.port).use { anon ->
                assertTrue(anon.isTokenRequired())
                assertFailsWith<HyperionAuthException> { anon.clear(50) }
            }
            HyperionClient("127.0.0.1", secured.port, token = "geheim").use { authed ->
                authed.clear(50)
                assertEquals("login", secured.requests.first { it.str("command") == "authorize" && it.str("subcommand") == "login" && it.str("token") == "geheim" }.str("subcommand"))
            }
        }
    }

    @Test
    fun `Base64 entspricht dem JDK`() {
        val random = kotlin.random.Random(42)
        repeat(50) {
            val data = random.nextBytes(random.nextInt(0, 40))
            assertEquals(java.util.Base64.getEncoder().encodeToString(data), Base64.encode(data))
        }
    }
}
