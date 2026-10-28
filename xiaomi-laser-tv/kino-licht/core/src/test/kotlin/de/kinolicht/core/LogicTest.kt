package de.kinolicht.core

import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RgbTest {
    @Test
    fun `HSV hin und zurueck`() {
        for (c in Presets.palette.map { it.color }) {
            val back = c.toHsv().toRgb()
            assertTrue(abs(back.r - c.r) <= 1 && abs(back.g - c.g) <= 1 && abs(back.b - c.b) <= 1, "$c → $back")
        }
    }

    @Test
    fun `Farbtemperaturen sind plausibel`() {
        val candle = Rgb.fromKelvin(1900)
        val day = Rgb.fromKelvin(6500)
        assertEquals(255, candle.r)
        assertTrue(candle.b < 60, "Kerzenlicht hat kaum Blau: $candle")
        assertTrue(day.r > 240 && day.g > 240 && day.b > 230, "6500 K ist nahezu weiß: $day")
    }

    @Test
    fun `Hex und Skalierung`() {
        assertEquals("#FF7800", Rgb(255, 120, 0).hex)
        assertEquals(Rgb(255, 120, 0), Rgb.fromHex("#ff7800"))
        assertEquals(Rgb(128, 60, 0), Rgb(255, 120, 0).scaled(0.5))
    }
}

class GradientTest {
    private val red = Rgb(255, 0, 0)
    private val blue = Rgb(0, 0, 255)

    @Test
    fun `Bild hat die richtige Groesse`() {
        assertEquals(64 * 36 * 3, GradientRenderer.render(listOf(red, blue), GradientStyle.AROUND).size)
    }

    @Test
    fun `horizontaler Verlauf laeuft von links nach rechts`() {
        assertEquals(red, GradientRenderer.colorAt(listOf(red, blue), GradientStyle.HORIZONTAL, 0.0, 0.5))
        assertEquals(blue, GradientRenderer.colorAt(listOf(red, blue), GradientStyle.HORIZONTAL, 1.0, 0.5))
    }

    @Test
    fun `gespiegelter Verlauf hat gleiche Raender`() {
        val left = GradientRenderer.colorAt(listOf(red, blue), GradientStyle.MIRROR, 0.0, 0.3)
        val right = GradientRenderer.colorAt(listOf(red, blue), GradientStyle.MIRROR, 1.0, 0.3)
        assertEquals(left, right)
        assertEquals(blue, GradientRenderer.colorAt(listOf(red, blue), GradientStyle.MIRROR, 0.5, 0.3))
    }

    @Test
    fun `Rundherum-Verlauf schliesst nahtlos`() {
        val colors = listOf(red, Rgb(0, 255, 0), blue)
        // Direkt links von der Mitte liegt der Übergang Ende → Anfang: oberhalb und unterhalb fast gleich.
        val above = GradientRenderer.colorAt(colors, GradientStyle.AROUND, 0.0, 0.499)
        val below = GradientRenderer.colorAt(colors, GradientStyle.AROUND, 0.0, 0.501)
        assertTrue(abs(above.r - below.r) < 10 && abs(above.b - below.b) < 10, "$above vs $below")
    }
}

class SunCalcTest {
    private val berlin = GeoLocation("Berlin", 52.520, 13.405)
    private val zone = ZoneId.of("Europe/Berlin")

    private fun assertNear(expected: String, actual: ZonedDateTime?, toleranceMin: Long = 4) {
        assertNotNull(actual)
        val diff = Duration.between(LocalTime.parse(expected), actual.toLocalTime()).abs().toMinutes()
        assertTrue(diff <= toleranceMin, "erwartet $expected, berechnet ${actual.toLocalTime()}")
    }

    @Test
    fun `Berlin Sommersonnenwende`() {
        val date = LocalDate.of(2024, 6, 21)
        assertNear("04:43", SunCalc.time(SunEvent.SUNRISE, date, berlin, zone))
        assertNear("21:33", SunCalc.time(SunEvent.SUNSET, date, berlin, zone))
    }

    @Test
    fun `Berlin Wintersonnenwende`() {
        val date = LocalDate.of(2024, 12, 21)
        assertNear("08:15", SunCalc.time(SunEvent.SUNRISE, date, berlin, zone))
        assertNear("15:54", SunCalc.time(SunEvent.SUNSET, date, berlin, zone))
    }

    @Test
    fun `Mitternachtssonne in Tromsoe`() {
        val tromso = GeoLocation("Tromsø", 69.65, 18.96)
        assertNull(SunCalc.time(SunEvent.SUNSET, LocalDate.of(2024, 6, 21), tromso, ZoneId.of("Europe/Oslo")))
    }

    @Test
    fun `New York in eigener Zeitzone`() {
        val ny = GeoLocation("New York", 40.713, -74.006)
        val sunset = SunCalc.time(SunEvent.SUNSET, LocalDate.of(2024, 6, 21), ny, ZoneId.of("America/New_York"))
        assertNear("20:31", sunset)
        assertEquals(LocalDate.of(2024, 6, 21), sunset!!.toLocalDate())
    }
}

class SchedulerTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val berlin = GeoLocation("Berlin", 52.520, 13.405)

    // Mittwoch, 2024-06-19
    private fun at(day: Int, hour: Int, minute: Int = 0) = ZonedDateTime.of(2024, 6, day, hour, minute, 0, 0, zone)

    @Test
    fun `feste Uhrzeit heute oder am naechsten passenden Tag`() {
        val rule = ScheduleRule(trigger = Trigger.AtTime(20, 30), days = ScheduleRule.WORKDAYS, action = RuleAction.TurnOn)
        assertEquals(at(19, 20, 30), Scheduler.nextRun(rule, at(19, 18), null))
        assertEquals(at(20, 20, 30), Scheduler.nextRun(rule, at(19, 21), null))
        // Freitagabend vorbei → Montag
        assertEquals(at(24, 20, 30), Scheduler.nextRun(rule, at(21, 21), null))
    }

    @Test
    fun `Sonnenuntergang mit Versatz`() {
        val rule = ScheduleRule(trigger = Trigger.AtSun(SunEvent.SUNSET, -30), action = RuleAction.TurnOn)
        val next = Scheduler.nextRun(rule, at(19, 12), berlin)!!
        assertEquals(19, next.dayOfMonth)
        assertTrue(next.toLocalTime().isAfter(LocalTime.of(20, 55)) && next.toLocalTime().isBefore(LocalTime.of(21, 10)), "$next")
    }

    @Test
    fun `ohne Standort keine Sonnen-Termine`() {
        val rule = ScheduleRule(trigger = Trigger.AtSun(SunEvent.SUNRISE), action = RuleAction.TurnOn)
        assertNull(Scheduler.nextRun(rule, at(19, 12), null))
    }

    @Test
    fun `deaktivierte Regel laeuft nie`() {
        val rule = ScheduleRule(enabled = false, trigger = Trigger.AtTime(20, 0), action = RuleAction.TurnOn)
        assertNull(Scheduler.nextRun(rule, at(19, 12), null))
    }

    @Test
    fun `Nacht ueber Mitternacht`() {
        val auto = Automation(nightStart = Trigger.AtTime(19, 0), nightEnd = Trigger.AtTime(6, 0))
        assertTrue(Scheduler.isNight(auto, at(19, 22), null))
        assertTrue(Scheduler.isNight(auto, at(19, 3), null))
        assertFalse(Scheduler.isNight(auto, at(19, 12), null))
    }

    @Test
    fun `lesbare Beschreibungen`() {
        assertEquals("Sonnenuntergang −30 min", Scheduler.describeTrigger(Trigger.AtSun(SunEvent.SUNSET, -30)))
        assertEquals("07:05", Scheduler.describeTrigger(Trigger.AtTime(7, 5)))
        assertEquals("Mo–Fr", Scheduler.describeDays(ScheduleRule.WORKDAYS))
        assertEquals("Mo, Mi", Scheduler.describeDays(setOf(3, 1)))
        assertEquals("heute 20:30", Scheduler.formatTime(at(19, 20, 30), at(19, 12)))
    }
}

class ModelsTest {
    @Test
    fun `Konfiguration uebersteht Speichern und Laden`() {
        val config = AppConfig(
            connection = ConnectionSettings(host = "192.168.1.5", token = "abc"),
            location = Presets.cities.first(),
            rules = listOf(
                ScheduleRule(trigger = Trigger.AtSun(SunEvent.SUNSET, 15), action = RuleAction.ApplyScene("abend")),
                ScheduleRule(trigger = Trigger.AtTime(23, 0), days = ScheduleRule.WEEKEND, action = RuleAction.FadeOut(10)),
            ),
            favoriteColors = listOf(Rgb(1, 2, 3)),
        )
        assertEquals(config, AppConfig.fromJson(config.toJson()))
    }

    @Test
    fun `kaputte Daten fallen auf Standard zurueck`() {
        assertEquals(AppConfig(), AppConfig.fromJson("{kaputt"))
        assertEquals(AppConfig(), AppConfig.fromJson(null))
    }
}

class LightControllerTest {
    private class RecordingApi(var state: HyperionState = HyperionState()) : HyperionApi {
        val calls = mutableListOf<String>()
        override suspend fun serverInfo() = state.also { calls += "serverinfo" }
        override suspend fun setColor(color: Rgb, priority: Int) { calls += "color ${color.hex} @$priority" }
        override suspend fun setEffect(name: String, priority: Int) { calls += "effect $name" }
        override suspend fun setImage(width: Int, height: Int, rgb: ByteArray, priority: Int, name: String) { calls += "image ${width}x$height" }
        override suspend fun clear(priority: Int) { calls += "clear $priority" }
        override suspend fun setComponent(component: String, enabled: Boolean) { calls += "component $component=$enabled" }
        override suspend fun setBrightness(brightness: Int) { calls += "brightness $brightness" }
    }

    @Test
    fun `Szene setzt erst Helligkeit, schaltet nur Noetiges ein`() = runBlocking {
        val api = RecordingApi(HyperionState(components = mapOf("ALL" to true, "LEDDEVICE" to false)))
        LightController(api, priority = 50).applyScene(
            Scene(name = "Test", icon = "x", action = LightAction.Solid(Rgb(255, 0, 0)), brightness = 30),
        )
        assertEquals(listOf("brightness 30", "serverinfo", "component LEDDEVICE=true", "color #FF0000 @50"), api.calls)
    }

    @Test
    fun `Verlauf wird als Bild gesendet`() = runBlocking {
        val api = RecordingApi()
        LightController(api, 50).apply(LightAction.Gradient(listOf(Rgb.WHITE, Rgb.BLACK)))
        assertEquals("image 64x36", api.calls.last())
    }

    @Test
    fun `Ausblenden endet aus und stellt Helligkeit wieder her`() = runBlocking {
        val api = RecordingApi()
        val progress = mutableListOf<Double>()
        LightController(api, 50, sleep = {}).fadeOut(from = 10, durationMs = 60_000, restoreBrightness = 80) { progress += it }
        val brightness = api.calls.filter { it.startsWith("brightness") }
        assertEquals("brightness 9", brightness.first())
        assertEquals("brightness 0", brightness[brightness.size - 2])
        assertEquals("component LEDDEVICE=false", api.calls[api.calls.size - 2])
        assertEquals("brightness 80", api.calls.last())
        assertEquals(1.0, progress.last())
    }

    @Test
    fun `Sonnenaufgang wird heller und weisser`() = runBlocking {
        val first = LightController.sunriseColor(0.0)
        val last = LightController.sunriseColor(1.0)
        assertTrue(first.r < 20 && first.b == 0, "$first")
        assertTrue(last.r > 240 && last.b > 200, "$last")
        val api = RecordingApi()
        LightController(api, 50, sleep = {}).sunrise(10_000)
        assertEquals(1 + 5, api.calls.count { it.startsWith("color") })
    }
}
