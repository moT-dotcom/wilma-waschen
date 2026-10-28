package de.kinolicht.core

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Sonnenauf- und -untergang nach dem Almanac-Verfahren (U.S. Naval Observatory),
 * Genauigkeit etwa ±2 Minuten – mehr als genug für Licht-Automatik.
 */
object SunCalc {
    private const val ZENITH = 90.833

    /** null, wenn die Sonne an diesem Tag nicht auf- bzw. untergeht (Polartag/-nacht). */
    fun time(event: SunEvent, date: LocalDate, location: GeoLocation, zone: ZoneId): ZonedDateTime? {
        val rising = event == SunEvent.SUNRISE
        val n = date.dayOfYear
        val lngHour = location.longitude / 15
        val t = n + ((if (rising) 6.0 else 18.0) - lngHour) / 24

        val m = 0.9856 * t - 3.289
        val l = norm360(m + 1.916 * sinD(m) + 0.020 * sinD(2 * m) + 282.634)

        var ra = norm360(atanD(0.91764 * tanD(l)))
        ra += floor(l / 90) * 90 - floor(ra / 90) * 90
        ra /= 15

        val sinDec = 0.39782 * sinD(l)
        val cosDec = cos(asin(sinDec))
        val cosH = (cosD(ZENITH) - sinDec * sinD(location.latitude)) / (cosDec * cosD(location.latitude))
        if (cosH > 1 || cosH < -1) return null

        val h = (if (rising) 360 - acosD(cosH) else acosD(cosH)) / 15
        val localMeanTime = h + ra - 0.06571 * t - 6.622
        val ut = (localMeanTime - lngHour).mod(24.0)

        val seconds = (ut * 3600).toLong().coerceIn(0, 86_399)
        val utc = ZonedDateTime.of(date, LocalTime.ofSecondOfDay(seconds), ZoneOffset.UTC)
        var local = utc.withZoneSameInstant(zone)
        // Für Zeitzonen weit weg von UTC kann das Ergebnis auf den Nachbartag fallen.
        if (local.toLocalDate().isBefore(date)) local = local.plusDays(1)
        if (local.toLocalDate().isAfter(date)) local = local.minusDays(1)
        return local
    }

    private fun norm360(v: Double) = v.mod(360.0)
    private fun rad(d: Double) = Math.toRadians(d)
    private fun deg(r: Double) = Math.toDegrees(r)
    private fun sinD(d: Double) = sin(rad(d))
    private fun cosD(d: Double) = cos(rad(d))
    private fun tanD(d: Double) = tan(rad(d))
    private fun atanD(v: Double) = deg(atan(v))
    private fun acosD(v: Double) = deg(acos(v))
}
