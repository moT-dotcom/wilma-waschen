@file:OptIn(ExperimentalLayoutApi::class)

package de.kinolicht.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.kinolicht.core.Presets

@Composable
fun EffectsScreen(vm: AppViewModel) {
    val config by vm.config.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val effects = state?.effects.orEmpty()
    val running = state?.visiblePriority?.takeIf { it.componentId == "EFFECT" }?.owner

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 48.dp)) {
        ScreenTitle("Effekte", "Animationen direkt aus deinem Hyperion · ☆ merkt Favoriten")
        if (effects.isEmpty()) {
            Spacer(Modifier.height(16.dp))
            InfoCard("Noch keine Effekte geladen. Ist die Verbindung zu Hyperion eingerichtet?", icon = "🔌")
            Spacer(Modifier.height(12.dp))
            TvButton("Neu laden", onClick = vm::refresh, icon = "⟳")
        } else {
            val favorites = config.favoriteEffects.filter { it in effects }
            if (favorites.isNotEmpty()) {
                SectionTitle("Favoriten")
                EffectGrid(favorites, favorites.toSet(), running, vm)
            }
            SectionTitle("Alle Effekte (${effects.size})")
            EffectGrid(effects, config.favoriteEffects.toSet(), running, vm)
        }
    }
}

@Composable
private fun EffectGrid(effects: List<String>, favorites: Set<String>, running: String?, vm: AppViewModel) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        effects.forEach { name ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                FocusBox(
                    onClick = { vm.startEffect(name) },
                    modifier = Modifier.size(width = 250.dp, height = 76.dp),
                    selected = name == running,
                    shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp, topEnd = 6.dp, bottomEnd = 6.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Column(Modifier.padding(horizontal = 18.dp)) {
                        Text(
                            (if (name == running) "▶ " else "") + Presets.effectLabel(name),
                            color = KL.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (Presets.effectLabel(name) != name) {
                            Text(name, color = KL.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Spacer(Modifier.width(4.dp))
                FocusBox(
                    onClick = { vm.toggleFavoriteEffect(name) },
                    modifier = Modifier.size(width = 52.dp, height = 76.dp),
                    shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 18.dp, bottomEnd = 18.dp),
                ) {
                    Text(if (name in favorites) "★" else "☆", color = if (name in favorites) KL.accent else KL.muted, fontSize = 22.sp)
                }
            }
        }
    }
}
