package de.kinolicht.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.kinolicht.app.CaptureService
import de.kinolicht.app.CaptureStatus

/** Bild-Sync: LEDs folgen dem Bild des Beamers (Ambilight), soweit Android das Bild herausgibt. */
@Composable
fun SyncScreen(vm: AppViewModel) {
    val config by vm.config.collectAsStateWithLifecycle()
    val status by vm.capture.collectAsStateWithLifecycle()
    val preview by vm.capturePreview.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            runCatching { CaptureService.start(context, result.resultCode, data) }
                .onSuccess { vm.say("📺 Bild-Sync läuft – jetzt zum Film wechseln") }
                .onFailure { vm.say("Bild-Sync konnte nicht starten: ${it.message}") }
        } else {
            vm.say("Ohne Erlaubnis zur Bildschirmaufnahme geht der Bild-Sync nicht")
        }
    }

    fun start() {
        if (!config.connection.isConfigured) {
            vm.say("Zuerst unter „Verbindung“ den Hyperion-Server eintragen")
            return
        }
        val manager = context.getSystemService(MediaProjectionManager::class.java)
        try {
            launcher.launch(manager.createScreenCaptureIntent())
        } catch (e: ActivityNotFoundException) {
            vm.say("Diese Firmware bietet keine Bildschirmaufnahme an")
        } catch (e: RuntimeException) {
            vm.say("Bildschirmaufnahme nicht möglich: ${e.message}")
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 48.dp)) {
        ScreenTitle("Bild-Sync", "Die LEDs nehmen die Farben des Bildes an – wie Ambilight")
        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (status.running) {
                    TvButton("Bild-Sync stoppen", icon = "■", primary = true, onClick = { CaptureService.stop(context) })
                } else {
                    TvButton("Bild-Sync starten", icon = "▶", primary = true, onClick = { start() })
                }
                StatusCard(status)
                DpadSlider(
                    label = "Bilder pro Sekunde",
                    value = config.syncFps,
                    range = 5..25,
                    onChange = vm::setSyncFps,
                    valueText = { "$it" },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (status.running) {
                    Text("Geänderte Bildrate gilt ab dem nächsten Start.", color = KL.muted, fontSize = 14.sp)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val shape = RoundedCornerShape(16.dp)
                Box(
                    Modifier
                        .size(width = 320.dp, height = 180.dp)
                        .clip(shape)
                        .background(Color.Black)
                        .border(1.dp, KL.border, shape),
                    contentAlignment = Alignment.Center,
                ) {
                    val frame = preview
                    if (status.running && frame != null) {
                        Image(
                            frame.asImageBitmap(),
                            contentDescription = "Aufgenommenes Bild",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds,
                            filterQuality = FilterQuality.Low,
                        )
                    } else {
                        Text("Keine Aufnahme", color = KL.muted, fontSize = 15.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Das sieht Kino-Licht gerade", color = KL.muted, fontSize = 14.sp)
            }
        }

        SectionTitle("Was funktioniert – und was nicht")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoCard("Apps auf dem Beamer ohne Kopierschutz, Menüs, Fotos, viele Mediaplayer: Die LEDs folgen dem Bild.", icon = "✅")
            InfoCard(
                "HDMI-Eingang (Apple TV) und geschützte Streaming-Apps: Android gibt dieses Bild Aufnahmen meist nicht heraus – " +
                    "es bleibt dann schwarz. Ob dein Beamer es kann, zeigt der Test unten.",
                icon = "⚠️",
            )
            InfoCard(
                "Test: Bild-Sync starten, zum Apple TV wechseln, eine Minute etwas Helles schauen und zurückkommen. " +
                    "Steht oben „davon … schwarz“, liefert der Beamer das HDMI-Bild nicht.",
                icon = "🔎",
            )
            InfoCard("Szenen haben Vorrang vor dem Bild-Sync. „Zurück zu Hyperion“ auf der Startseite gibt den Sync wieder frei.", icon = "💡")
        }
    }
}

@Composable
private fun StatusCard(status: CaptureStatus) {
    val (icon, title, detail) = when {
        status.error != null && !status.running -> Triple("⚠️", "Nicht gestartet", status.error.orEmpty())
        !status.running && status.seconds > 0 -> Triple("■", "Gestoppt", summary(status))
        !status.running -> Triple("○", "Aus", "Startet erst, wenn du es erlaubst – Android fragt einmal nach.")
        status.error != null -> Triple("⚠️", "Hyperion nicht erreichbar", status.error.orEmpty())
        status.black -> Triple("⬛", "Läuft, aber das Bild ist schwarz", summary(status))
        else -> Triple("🟢", "Läuft · ${status.fps} Bilder/s", summary(status))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (status.running && status.black) KL.selected else KL.surface)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 22.sp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = KL.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = KL.muted, fontSize = 15.sp)
        }
    }
}

private fun summary(status: CaptureStatus): String {
    fun time(seconds: Int) = if (seconds < 60) "$seconds s" else "${seconds / 60} min"
    return if (status.blackSeconds == 0) {
        "Seit ${time(status.seconds)} aktiv, Bild durchgehend sichtbar"
    } else {
        "Seit ${time(status.seconds)} aktiv, davon ${time(status.blackSeconds)} schwarz"
    }
}
