package de.kinolicht.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KinoTheme { MainScreen(vm) } }
    }

    override fun onStart() {
        super.onStart()
        vm.setVisible(true)
    }

    override fun onStop() {
        vm.setVisible(false)
        super.onStop()
    }
}

enum class Tab(val label: String, val icon: String) {
    NOW("Jetzt", "🏠"),
    COLORS("Farben", "🎨"),
    EFFECTS("Effekte", "✨"),
    SCENES("Szenen", "🎬"),
    SCHEDULE("Zeitplan", "⏰"),
    AUTOMATION("Automatik", "🤖"),
    SETTINGS("Verbindung", "🔌"),
}

@Composable
fun MainScreen(vm: AppViewModel) {
    val config by vm.config.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(if (config.connection.isConfigured) Tab.NOW else Tab.SETTINGS) }

    Box(Modifier.fillMaxSize().background(KL.bg)) {
        Row(Modifier.fillMaxSize()) {
            NavRail(selected = tab, onSelect = { tab = it })
            Column(Modifier.weight(1f).fillMaxHeight().padding(start = 16.dp, end = 48.dp, top = 28.dp)) {
                StatusBar(vm)
                Spacer(Modifier.height(12.dp))
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        Tab.NOW -> NowScreen(vm, onOpenSettings = { tab = Tab.SETTINGS })
                        Tab.COLORS -> ColorsScreen(vm)
                        Tab.EFFECTS -> EffectsScreen(vm)
                        Tab.SCENES -> ScenesScreen(vm)
                        Tab.SCHEDULE -> ScheduleScreen(vm)
                        Tab.AUTOMATION -> AutomationScreen(vm)
                        Tab.SETTINGS -> SettingsScreen(vm)
                    }
                }
            }
        }
        MessageOverlay(vm, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun NavRail(selected: Tab, onSelect: (Tab) -> Unit) {
    val requesters = remember { Tab.entries.associateWith { FocusRequester() } }
    Column(
        Modifier
            .width(240.dp)
            .fillMaxHeight()
            .background(Color(0xFF0E0F14))
            .padding(horizontal = 18.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, bottom = 22.dp)) {
            Box(
                Modifier.size(14.dp).clip(CircleShape).background(KL.accent),
            )
            Spacer(Modifier.width(10.dp))
            Text("Kino-Licht", color = KL.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Tab.entries.forEach { t ->
            FocusBox(
                onClick = { onSelect(t) },
                onFocus = { onSelect(t) },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                selected = t == selected,
                background = Color.Transparent,
                focusScale = 1.03f,
                focusRequester = requesters.getValue(t),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(t.icon, fontSize = 20.sp)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        t.label,
                        color = if (t == selected) KL.text else KL.muted,
                        fontSize = 18.sp,
                        fontWeight = if (t == selected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { requesters.getValue(selected).requestFocus() } }
}

@Composable
private fun StatusBar(vm: AppViewModel) {
    val status by vm.status.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val config by vm.config.collectAsStateWithLifecycle()
    val sleepEndsAt by vm.sleepEndsAt.collectAsStateWithLifecycle()
    val task by vm.task.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sleepEndsAt) {
        while (sleepEndsAt != null) {
            now = System.currentTimeMillis()
            delay(10_000)
        }
    }

    val (dot, text) = when (val s = status) {
        ConnectionStatus.NotConfigured -> KL.muted to "Noch kein Hyperion-Server eingerichtet"
        ConnectionStatus.Connecting -> KL.accent to "Verbinde mit ${config.connection.host} …"
        is ConnectionStatus.Failed -> KL.bad to s.message
        ConnectionStatus.Connected -> {
            val st = state
            val name = st?.hostname ?: config.connection.host
            KL.ok to buildString {
                append(name)
                st?.version?.let { append(" · Hyperion $it") }
                st?.let { append(" · ").append(it.describeSource(config.connection.priority)) }
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(10.dp))
        Text(text, color = KL.muted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        // Beim Einschlaf-Timer zählen die Restminuten, erst beim Ausblenden der Fortschritt.
        val fading = task?.endsWith("%") == true
        val badge = if (fading) task else sleepEndsAt?.let { "😴 noch ${((it - now) / 60_000).coerceAtLeast(0) + 1} min" } ?: task
        if (badge != null) {
            Text(
                badge,
                color = KL.accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(KL.selected)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun MessageOverlay(vm: AppViewModel, modifier: Modifier) {
    val message by vm.message.collectAsStateWithLifecycle()
    var shown by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) {
        if (message != null) {
            shown = message
            delay(3200)
            vm.clearMessage()
        }
    }
    AnimatedVisibility(
        visible = message != null,
        modifier = modifier.padding(bottom = 36.dp),
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        Text(
            shown.orEmpty(),
            color = KL.text,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(KL.surface2)
                .border(1.dp, KL.border, RoundedCornerShape(28.dp))
                .padding(horizontal = 28.dp, vertical = 14.dp),
        )
    }
}
