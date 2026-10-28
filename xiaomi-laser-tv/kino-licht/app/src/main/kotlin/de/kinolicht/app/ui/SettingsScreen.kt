@file:OptIn(ExperimentalLayoutApi::class)

package de.kinolicht.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.kinolicht.core.GeoLocation
import de.kinolicht.core.HyperionClient
import de.kinolicht.core.Presets
import de.kinolicht.core.SunCalc
import de.kinolicht.core.SunEvent
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val config by vm.config.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val servers by vm.discovery.servers.collectAsStateWithLifecycle()
    val searching by vm.discovery.searching.collectAsStateWithLifecycle()
    val tokenCode by vm.tokenRequest.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<String?>(null) }
    var customLat by remember { mutableStateOf<Double?>(null) }
    val conn = config.connection
    val context = LocalContext.current

    DisposableEffect(Unit) { onDispose { vm.discovery.stop() } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 48.dp)) {
        ScreenTitle("Verbindung", "Hyperion.ng auf dem Raspberry Pi · JSON-Schnittstelle Port ${HyperionClient.DEFAULT_PORT}")

        Spacer(Modifier.height(16.dp))
        when (val s = status) {
            ConnectionStatus.Connected -> InfoCard(
                buildString {
                    append("Verbunden")
                    state?.version?.let { append(" · Hyperion $it") }
                    state?.let { st ->
                        if (st.ledCount > 0) append(" · ${st.ledCount} LEDs")
                        if (st.effects.isNotEmpty()) append(" · ${st.effects.size} Effekte")
                    }
                },
                icon = "✅",
            )
            is ConnectionStatus.Failed -> InfoCard(s.message, icon = "⚠️")
            ConnectionStatus.Connecting -> InfoCard("Verbinde …", icon = "⏳")
            ConnectionStatus.NotConfigured -> InfoCard("Am einfachsten: „Im Netzwerk suchen“. Kino-Licht findet Hyperion automatisch.", icon = "👋")
        }

        SectionTitle("Server")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton(
                if (searching) "Suche läuft …" else "Im Netzwerk suchen",
                icon = "🔍",
                primary = !conn.isConfigured,
                onClick = { if (searching) vm.discovery.stop() else vm.discovery.start() },
            )
            TvButton("Verbindung testen", icon = "⚡", onClick = vm::testConnection)
        }
        if (servers.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                servers.forEach { server ->
                    SettingRow(
                        title = server.name,
                        value = "${server.host}:${server.port}" + if (server.host == conn.host) "  ✓" else "",
                        icon = "🖥",
                        onClick = {
                            vm.updateConnection { it.copy(host = server.host, port = server.port) }
                            vm.discovery.stop()
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingRow("Adresse", conn.host.ifBlank { "nicht gesetzt" }, onClick = { dialog = "host" }, icon = "🌐")
            SettingRow("Port", conn.port.toString(), onClick = { dialog = "port" }, icon = "🔢")
        }

        SectionTitle("Zugriffsschutz", hint = "Nur nötig, wenn in Hyperion „API-Authentifizierung“ aktiv ist")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingRow("Token", if (conn.token.isNullOrBlank()) "keins" else "••••••" + conn.token.orEmpty().takeLast(4), onClick = { dialog = "token" }, icon = "🔑")
            if (tokenCode != null) {
                InfoCard("Bitte jetzt in der Hyperion-Weboberfläche die Anfrage mit dem Code $tokenCode bestätigen.", icon = "⏳")
            } else {
                TvButton("Token automatisch anfordern", icon = "🪄", onClick = vm::requestToken)
            }
        }

        if ((state?.instances?.size ?: 0) > 1) {
            SectionTitle("Hyperion-Instanz")
            SettingRow(
                "Instanz",
                state?.instances?.firstOrNull { it.instance == conn.instance }?.name ?: "Instanz ${conn.instance}",
                onClick = { dialog = "instance" },
                icon = "🧩",
            )
        }

        SectionTitle("Priorität", hint = "Kleiner = wichtiger. Hyperions eigene Quellen liegen bei 240+, die Weboberfläche bei 1.")
        DpadSlider(
            label = "Priorität von Kino-Licht",
            value = conn.priority,
            range = 2..199,
            onChange = { p -> vm.updateConnection { it.copy(priority = p) } },
            modifier = Modifier.fillMaxWidth(),
        )

        SectionTitle("Standort", hint = "Für Sonnenauf- und -untergang – wird nur auf dem Beamer berechnet, nichts verlässt das Gerät")
        SettingRow("Ort", config.location?.name ?: "nicht gesetzt", onClick = { dialog = "city" }, icon = "📍")
        config.location?.let { loc ->
            val zone = ZoneId.systemDefault()
            val fmt = DateTimeFormatter.ofPattern("HH:mm")
            val rise = SunCalc.time(SunEvent.SUNRISE, LocalDate.now(), loc, zone)?.format(fmt) ?: "–"
            val set = SunCalc.time(SunEvent.SUNSET, LocalDate.now(), loc, zone)?.format(fmt) ?: "–"
            Text("Heute: Sonnenaufgang $rise · Sonnenuntergang $set", color = KL.muted, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
        }

        SectionTitle("Über")
        val version = remember {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
        }
        Text("Kino-Licht $version · steuert Hyperion.ng über die JSON-API", color = KL.muted, fontSize = 15.sp)
        Spacer(Modifier.height(12.dp))
        TvButton("Szenen und Zeitpläne zurücksetzen", icon = "↺", onClick = { dialog = "reset" })
    }

    when (dialog) {
        "host" -> TextInputDialog(
            "Adresse des Raspberry Pi", conn.host,
            hint = "IP-Adresse oder Hostname, z. B. 192.168.178.40 oder hyperion.local",
            keyboardType = KeyboardType.Uri,
            onDismiss = { dialog = null },
            onConfirm = { v -> vm.updateConnection { it.copy(host = v) }; dialog = null },
        )
        "port" -> TextInputDialog(
            "Port der JSON-Schnittstelle", conn.port.toString(),
            hint = "Standard bei Hyperion.ng: ${HyperionClient.DEFAULT_PORT}",
            keyboardType = KeyboardType.Number,
            onDismiss = { dialog = null },
            onConfirm = { v -> v.toIntOrNull()?.takeIf { it in 1..65535 }?.let { p -> vm.updateConnection { it.copy(port = p) } }; dialog = null },
        )
        "token" -> TextInputDialog(
            "API-Token", conn.token.orEmpty(),
            hint = "Hyperion-Weboberfläche → Netzwerkdienste → API-Authentifizierung → Token erstellen. Leer lassen zum Entfernen.",
            onDismiss = { dialog = null },
            onConfirm = { v -> vm.updateConnection { it.copy(token = v.ifBlank { null }) }; dialog = null },
        )
        "instance" -> ChoiceDialog(
            "Instanz wählen",
            options = state?.instances.orEmpty(),
            label = { "${it.name} (#${it.instance})" + if (it.running) "" else " · gestoppt" },
            selected = state?.instances?.firstOrNull { it.instance == conn.instance },
            onPick = { inst -> vm.updateConnection { it.copy(instance = inst.instance) }; dialog = null },
            onDismiss = { dialog = null },
        )
        "city" -> ChoiceDialog(
            "Standort wählen",
            options = listOf<GeoLocation?>(null) + Presets.cities,
            label = { it?.name ?: "✎  Eigene Koordinaten eingeben …" },
            selected = Presets.cities.firstOrNull { it == config.location },
            onPick = { loc -> if (loc == null) dialog = "lat" else { vm.setLocation(loc); dialog = null } },
            onDismiss = { dialog = null },
        )
        "lat" -> TextInputDialog(
            "Breitengrad", config.location?.latitude?.toString().orEmpty(),
            hint = "z. B. 51.34 (Norden positiv)",
            keyboardType = KeyboardType.Decimal,
            onDismiss = { dialog = null },
            onConfirm = { v ->
                customLat = v.replace(',', '.').toDoubleOrNull()?.takeIf { it in -90.0..90.0 }
                dialog = if (customLat != null) "lon" else null
            },
        )
        "lon" -> TextInputDialog(
            "Längengrad", config.location?.longitude?.toString().orEmpty(),
            hint = "z. B. 12.37 (Osten positiv)",
            keyboardType = KeyboardType.Decimal,
            onDismiss = { dialog = null },
            onConfirm = { v ->
                val lon = v.replace(',', '.').toDoubleOrNull()?.takeIf { it in -180.0..180.0 }
                val lat = customLat
                if (lon != null && lat != null) vm.setLocation(GeoLocation("Eigener Ort (%.2f, %.2f)".format(lat, lon), lat, lon))
                dialog = null
            },
        )
        "reset" -> TvDialog("Wirklich zurücksetzen?", onDismiss = { dialog = null }) {
            Text("Eigene Szenen, Favoriten, Zeitpläne und Automatik werden auf den Auslieferungszustand gesetzt. Verbindung und Standort bleiben.", color = KL.muted, fontSize = 16.sp)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton("Zurücksetzen", primary = true, icon = "↺", onClick = { vm.resetAll(); dialog = null })
                TvButton("Abbrechen", onClick = { dialog = null })
            }
        }
    }
}
