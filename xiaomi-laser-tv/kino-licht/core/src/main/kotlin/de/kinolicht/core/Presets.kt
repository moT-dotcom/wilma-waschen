package de.kinolicht.core

data class NamedColor(val name: String, val color: Rgb)
data class NamedWhite(val name: String, val kelvin: Int)
data class NamedGradient(val name: String, val icon: String, val colors: List<Rgb>, val style: GradientStyle)

object Presets {

    val palette: List<NamedColor> = listOf(
        NamedColor("Rot", Rgb(255, 0, 0)),
        NamedColor("Koralle", Rgb(255, 80, 60)),
        NamedColor("Orange", Rgb(255, 110, 0)),
        NamedColor("Bernstein", Rgb(255, 160, 20)),
        NamedColor("Gold", Rgb(255, 200, 40)),
        NamedColor("Gelb", Rgb(255, 240, 0)),
        NamedColor("Limette", Rgb(170, 255, 0)),
        NamedColor("Grün", Rgb(0, 255, 40)),
        NamedColor("Smaragd", Rgb(0, 200, 110)),
        NamedColor("Türkis", Rgb(0, 230, 200)),
        NamedColor("Cyan", Rgb(0, 220, 255)),
        NamedColor("Himmelblau", Rgb(40, 150, 255)),
        NamedColor("Blau", Rgb(0, 40, 255)),
        NamedColor("Indigo", Rgb(75, 0, 255)),
        NamedColor("Violett", Rgb(140, 0, 255)),
        NamedColor("Lila", Rgb(200, 0, 255)),
        NamedColor("Magenta", Rgb(255, 0, 220)),
        NamedColor("Pink", Rgb(255, 40, 150)),
        NamedColor("Rosé", Rgb(255, 120, 160)),
        NamedColor("Lavendel", Rgb(180, 140, 255)),
        NamedColor("Mint", Rgb(120, 255, 190)),
        NamedColor("Pfirsich", Rgb(255, 170, 120)),
        NamedColor("Kinorot", Rgb(150, 0, 20)),
        NamedColor("Nachtblau", Rgb(10, 20, 90)),
    )

    val whites: List<NamedWhite> = listOf(
        NamedWhite("Kerze", 1900),
        NamedWhite("Glühbirne", 2700),
        NamedWhite("Warmweiß", 3000),
        NamedWhite("Neutral", 4000),
        NamedWhite("Tageslicht", 5500),
        NamedWhite("Kaltweiß", 6500),
    )

    val gradients: List<NamedGradient> = listOf(
        NamedGradient("Sonnenuntergang", "🌅", listOf(Rgb(255, 90, 0), Rgb(255, 30, 80), Rgb(110, 0, 180)), GradientStyle.AROUND),
        NamedGradient("Nordlicht", "🌌", listOf(Rgb(0, 255, 120), Rgb(0, 140, 255), Rgb(160, 0, 255)), GradientStyle.AROUND),
        NamedGradient("Ozean", "🌊", listOf(Rgb(0, 40, 160), Rgb(0, 200, 220)), GradientStyle.VERTICAL),
        NamedGradient("Feuer", "🔥", listOf(Rgb(255, 20, 0), Rgb(255, 140, 0)), GradientStyle.MIRROR),
        NamedGradient("Neon", "💜", listOf(Rgb(255, 0, 200), Rgb(0, 220, 255)), GradientStyle.HORIZONTAL),
        NamedGradient("Wald", "🌲", listOf(Rgb(0, 120, 30), Rgb(160, 220, 0)), GradientStyle.AROUND),
        NamedGradient("Bonbon", "🍬", listOf(Rgb(255, 120, 180), Rgb(140, 200, 255), Rgb(255, 240, 150)), GradientStyle.AROUND),
        NamedGradient("Kino", "🎬", listOf(Rgb(120, 0, 20), Rgb(255, 120, 30)), GradientStyle.MIRROR),
    )

    val defaultScenes: List<Scene> = listOf(
        Scene("film", "Film", "🎬", LightAction.Solid(Rgb.fromKelvin(2700)), brightness = 8),
        Scene("pause", "Pause", "☕", LightAction.Solid(Rgb.fromKelvin(2700)), brightness = 60),
        Scene("abend", "Abend", "🌅", LightAction.Gradient(gradients[0].colors, GradientStyle.AROUND), brightness = 45),
        Scene("ambiente", "Ambiente", "✨", LightAction.Effect("Warm mood blobs"), brightness = 50),
        Scene("kerze", "Kerzenschein", "🕯️", LightAction.Effect("Candle"), brightness = 60),
        Scene("party", "Party", "🎉", LightAction.Effect("Rainbow swirl"), brightness = 100),
        Scene("hyperion", "Hyperion", "↩️", LightAction.HyperionDefault),
        Scene("aus", "Aus", "⏻", LightAction.Off),
    )

    val sceneIcons: List<String> = listOf(
        "🎬", "🍿", "☕", "🌅", "🌙", "✨", "🕯️", "🎉", "🌈", "🌊", "🔥", "🌲",
        "💜", "❤️", "💡", "📺", "🎮", "🎵", "📖", "🛋️", "😴", "⭐", "❄️", "🎄",
    )

    /** Deutsche Namen für die mitgelieferten Hyperion.ng-Effekte. Unbekannte bleiben, wie sie sind. */
    val effectNames: Map<String, String> = mapOf(
        "Atomic swirl" to "Atom-Wirbel",
        "Blue mood blobs" to "Blaue Stimmungsblasen",
        "Breath" to "Atmen",
        "Candle" to "Kerze",
        "Cinema brighten lights" to "Kino: Licht an",
        "Cinema dim lights" to "Kino: Licht dimmen",
        "Cold mood blobs" to "Kühle Stimmungsblasen",
        "Collision" to "Kollision",
        "Double swirl" to "Doppelwirbel",
        "Fire" to "Feuer",
        "Flags Germany/Sweden" to "Flaggen",
        "Full color mood blobs" to "Bunte Stimmungsblasen",
        "Green mood blobs" to "Grüne Stimmungsblasen",
        "Knight rider" to "Knight Rider",
        "Led Test" to "LED-Test",
        "Light clock" to "Lichtuhr",
        "Lights" to "Lichter",
        "Matrix" to "Matrix",
        "Notify blue" to "Hinweis blau",
        "Pac-Man" to "Pac-Man",
        "Plasma" to "Plasma",
        "Police Lights Single" to "Blaulicht",
        "Police Lights Solid" to "Blaulicht flächig",
        "Rainbow mood" to "Regenbogen-Stimmung",
        "Rainbow swirl" to "Regenbogen-Wirbel",
        "Rainbow swirl fast" to "Regenbogen-Wirbel schnell",
        "Random" to "Zufall",
        "Red mood blobs" to "Rote Stimmungsblasen",
        "Running dots" to "Laufende Punkte",
        "Sea waves" to "Meereswellen",
        "Snake" to "Schlange",
        "Sparks" to "Funken",
        "Strobe red" to "Stroboskop rot",
        "Strobe white" to "Stroboskop weiß",
        "System Shutdown" to "System aus",
        "Trails" to "Spuren",
        "Trails color" to "Farbspuren",
        "Warm mood blobs" to "Warme Stimmungsblasen",
        "Waves with Color" to "Farbwellen",
        "X-Mas" to "Weihnachten",
    )

    fun effectLabel(name: String): String = effectNames[name] ?: name

    val cities: List<GeoLocation> = listOf(
        GeoLocation("Berlin", 52.520, 13.405),
        GeoLocation("Hamburg", 53.551, 9.994),
        GeoLocation("München", 48.137, 11.575),
        GeoLocation("Köln", 50.938, 6.960),
        GeoLocation("Frankfurt", 50.110, 8.682),
        GeoLocation("Stuttgart", 48.776, 9.183),
        GeoLocation("Düsseldorf", 51.227, 6.774),
        GeoLocation("Leipzig", 51.340, 12.375),
        GeoLocation("Dresden", 51.050, 13.738),
        GeoLocation("Hannover", 52.376, 9.732),
        GeoLocation("Nürnberg", 49.454, 11.077),
        GeoLocation("Bremen", 53.079, 8.802),
        GeoLocation("Wien", 48.208, 16.373),
        GeoLocation("Zürich", 47.377, 8.541),
        GeoLocation("Amsterdam", 52.368, 4.904),
        GeoLocation("Prag", 50.075, 14.438),
        GeoLocation("Warschau", 52.230, 21.012),
        GeoLocation("Paris", 48.857, 2.352),
        GeoLocation("Madrid", 40.417, -3.704),
        GeoLocation("Rom", 41.903, 12.496),
        GeoLocation("Istanbul", 41.008, 28.978),
        GeoLocation("Dubai", 25.205, 55.271),
        GeoLocation("Bangkok", 13.756, 100.502),
        GeoLocation("New York", 40.713, -74.006),
    )
}
