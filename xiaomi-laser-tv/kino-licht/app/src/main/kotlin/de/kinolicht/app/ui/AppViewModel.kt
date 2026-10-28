package de.kinolicht.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.kinolicht.app.HyperionDiscovery
import de.kinolicht.app.KinoLichtApp
import de.kinolicht.app.LightRuntime
import de.kinolicht.app.LightService
import de.kinolicht.app.newClient
import de.kinolicht.core.AppConfig
import de.kinolicht.core.Automation
import de.kinolicht.core.ConnectionSettings
import de.kinolicht.core.GeoLocation
import de.kinolicht.core.GradientRenderer
import de.kinolicht.core.HyperionClient
import de.kinolicht.core.HyperionException
import de.kinolicht.core.HyperionState
import de.kinolicht.core.LightAction
import de.kinolicht.core.LightController
import de.kinolicht.core.Presets
import de.kinolicht.core.Rgb
import de.kinolicht.core.Scene
import de.kinolicht.core.ScheduleRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface ConnectionStatus {
    data object NotConfigured : ConnectionStatus
    data object Connecting : ConnectionStatus
    data object Connected : ConnectionStatus
    data class Failed(val message: String) : ConnectionStatus
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as KinoLichtApp
    private val store = app.store

    val config: StateFlow<AppConfig> = store.config
    val sleepEndsAt: StateFlow<Long?> = LightRuntime.sleepEndsAt
    val task: StateFlow<String?> = LightRuntime.task
    val discovery = HyperionDiscovery(application)

    private val _state = MutableStateFlow<HyperionState?>(null)
    val state: StateFlow<HyperionState?> = _state.asStateFlow()

    private val _status = MutableStateFlow<ConnectionStatus>(ConnectionStatus.NotConfigured)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Läuft gerade eine Token-Anfrage? Enthält dann den Code, den Hyperion anzeigt. */
    private val _tokenRequest = MutableStateFlow<String?>(null)
    val tokenRequest: StateFlow<String?> = _tokenRequest.asStateFlow()

    /** Aktuelle Arbeitsfarbe der Farbseite. */
    val workColor = MutableStateFlow(Rgb.fromKelvin(2700))

    private var client: HyperionClient? = null
    private val clientLock = Mutex()
    private var visible = true

    // Live-Vorschau: StateFlows verwerfen Zwischenwerte, wenn Hyperion langsamer ist als das Steuerkreuz.
    // Die Sequenznummer sorgt dafür, dass auch ein erneut gewählter gleicher Wert gesendet wird.
    private data class Live<T>(val value: T, val seq: Long)
    private var liveSeq = 0L
    private val liveAction = MutableStateFlow<Live<LightAction>?>(null)
    private val liveBrightness = MutableStateFlow<Live<Int>?>(null)
    private var rememberJob: Job? = null

    init {
        viewModelScope.launch {
            config.map { it.connection }.distinctUntilChanged().collect {
                resetClient()
                refreshNow()
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(5000)
                if (visible) refreshNow()
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            liveAction.filterNotNull().collect { (action, _) ->
                runLight(quiet = true) { c ->
                    if (_state.value?.ledsOn == false) turnOn()
                    when (action) {
                        is LightAction.Solid -> c.setColor(action.color, config.value.connection.priority)
                        is LightAction.Gradient -> c.setImage(
                            GradientRenderer.WIDTH, GradientRenderer.HEIGHT,
                            GradientRenderer.render(action.colors, action.style),
                            config.value.connection.priority, "Kino-Licht Verlauf",
                        )
                        else -> apply(action)
                    }
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            liveBrightness.filterNotNull().collect { (value, _) ->
                runLight(quiet = true) { c -> c.setBrightness(value) }
            }
        }
    }

    fun setVisible(isVisible: Boolean) {
        visible = isVisible
        if (isVisible) viewModelScope.launch { refreshNow() } else discovery.stop()
    }

    fun clearMessage() { _message.value = null }

    fun refresh() { viewModelScope.launch { refreshNow() } }

    // ── Licht ────────────────────────────────────────────────────────────────

    fun applyScene(scene: Scene) {
        launchLight("${scene.icon} ${scene.name}") { applyScene(scene) }
        rememberScene(scene)
    }

    /** Farbe oder Verlauf sofort zeigen (für Paletten, Regler, Verlaufseditor). */
    fun preview(action: LightAction, label: String = "Zuletzt gewählt") {
        liveAction.value = Live(action, ++liveSeq)
        if (action is LightAction.Solid) workColor.value = action.color
        rememberJob?.cancel()
        rememberJob = viewModelScope.launch {
            delay(1500)
            rememberScene(Scene(id = "zuletzt", name = label, icon = "🎨", action = action))
        }
    }

    fun setBrightness(value: Int) {
        liveBrightness.value = Live(value, ++liveSeq)
        _state.value = _state.value?.copy(brightness = value)
        store.update { it.copy(userBrightness = value) }
    }

    fun setPower(on: Boolean) {
        launchLight(if (on) "LEDs an" else "LEDs aus") { if (on) turnOn() else turnOff() }
    }

    fun backToHyperion() {
        launchLight("Hyperion übernimmt wieder") { apply(LightAction.HyperionDefault) }
    }

    fun startEffect(name: String) {
        launchLight("✨ ${Presets.effectLabel(name)}") { apply(LightAction.Effect(name)) }
        rememberScene(Scene(id = "zuletzt", name = Presets.effectLabel(name), icon = "✨", action = LightAction.Effect(name)))
    }

    fun startSleepTimer(minutes: Int) {
        LightService.start(getApplication(), LightService.ACTION_SLEEP) { putExtra(LightService.EXTRA_MINUTES, minutes) }
        _message.value = "😴 Einschlaf-Timer: $minutes min"
    }

    fun cancelTimer() {
        LightService.start(getApplication(), LightService.ACTION_CANCEL_SLEEP)
        _message.value = "Timer beendet"
    }

    fun testRule(rule: ScheduleRule) {
        LightService.start(getApplication(), LightService.ACTION_TEST_RULE) {
            putExtra(LightService.EXTRA_RULE, AppConfig.json.encodeToString(ScheduleRule.serializer(), rule))
        }
        _message.value = "▶ Regel wird ausgeführt"
    }

    // ── Favoriten, Szenen, Regeln ────────────────────────────────────────────

    fun toggleFavoriteColor(color: Rgb) = store.update {
        val favs = if (color in it.favoriteColors) it.favoriteColors - color else (listOf(color) + it.favoriteColors).take(16)
        it.copy(favoriteColors = favs)
    }

    fun toggleFavoriteEffect(name: String) = store.update {
        it.copy(favoriteEffects = if (name in it.favoriteEffects) it.favoriteEffects - name else it.favoriteEffects + name)
    }

    fun saveScene(scene: Scene) {
        store.update { cfg ->
            val exists = cfg.scenes.any { it.id == scene.id }
            cfg.copy(scenes = if (exists) cfg.scenes.map { if (it.id == scene.id) scene else it } else cfg.scenes + scene)
        }
        _message.value = "Szene „${scene.name}“ gespeichert"
    }

    fun deleteScene(id: String) = store.update { cfg ->
        cfg.copy(
            scenes = cfg.scenes.filterNot { it.id == id },
            automation = cfg.automation.copy(
                daySceneId = cfg.automation.daySceneId.takeIf { it != id },
                nightSceneId = cfg.automation.nightSceneId.takeIf { it != id },
            ),
        )
    }

    fun moveScene(id: String, delta: Int) = store.update { cfg ->
        val list = cfg.scenes.toMutableList()
        val from = list.indexOfFirst { it.id == id }
        if (from < 0) return@update cfg
        val to = (from + delta).coerceIn(0, list.lastIndex)
        list.add(to, list.removeAt(from))
        cfg.copy(scenes = list)
    }

    fun saveRule(rule: ScheduleRule) {
        store.update { cfg ->
            val exists = cfg.rules.any { it.id == rule.id }
            cfg.copy(rules = if (exists) cfg.rules.map { if (it.id == rule.id) rule else it } else cfg.rules + rule)
        }
        _message.value = "Zeitplan gespeichert"
    }

    fun deleteRule(id: String) = store.update { cfg -> cfg.copy(rules = cfg.rules.filterNot { it.id == id }) }

    fun setRuleEnabled(id: String, enabled: Boolean) = store.update { cfg ->
        cfg.copy(rules = cfg.rules.map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    fun updateAutomation(transform: (Automation) -> Automation) = store.update { it.copy(automation = transform(it.automation)) }

    fun updateConnection(transform: (ConnectionSettings) -> ConnectionSettings) = store.update { it.copy(connection = transform(it.connection)) }

    fun setLocation(location: GeoLocation?) = store.update { it.copy(location = location) }

    fun resetAll() {
        store.update { AppConfig(connection = it.connection, location = it.location) }
        _message.value = "Szenen und Zeitpläne zurückgesetzt"
    }

    // ── Verbindung ───────────────────────────────────────────────────────────

    fun testConnection() {
        viewModelScope.launch {
            refreshNow()
            _message.value = when (val s = _status.value) {
                ConnectionStatus.Connected -> "✓ Verbunden mit Hyperion ${_state.value?.version ?: ""}".trim()
                is ConnectionStatus.Failed -> s.message
                else -> null
            }
        }
    }

    fun requestToken() {
        val cfg = config.value.connection
        if (!cfg.isConfigured) return
        val code = (1..5).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }.joinToString("")
        _tokenRequest.value = code
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = cfg.copy(token = null).newClient(readTimeoutMs = 180_000)
                    .use { it.requestToken("Kino-Licht auf dem Beamer", code) }
                updateConnection { it.copy(token = token) }
                _message.value = "🔑 Token erhalten und gespeichert"
            } catch (e: HyperionException) {
                _message.value = "Token-Anfrage: ${e.message}"
            } finally {
                _tokenRequest.value = null
            }
        }
    }

    private suspend fun refreshNow() {
        if (!config.value.connection.isConfigured) {
            _status.value = ConnectionStatus.NotConfigured
            _state.value = null
            return
        }
        if (_status.value !is ConnectionStatus.Connected) _status.value = ConnectionStatus.Connecting
        try {
            _state.value = withClient { it.fetchState() }
            _status.value = ConnectionStatus.Connected
        } catch (e: HyperionException) {
            _status.value = ConnectionStatus.Failed(e.message ?: "Verbindungsfehler")
        }
    }

    private suspend fun resetClient() = clientLock.withLock {
        client?.close()
        client = null
    }

    private suspend fun <T> withClient(block: suspend (HyperionClient) -> T): T {
        val c = clientLock.withLock { client ?: config.value.connection.newClient().also { client = it } }
        return block(c)
    }

    private suspend fun runLight(quiet: Boolean = false, block: suspend LightController.(HyperionClient) -> Unit): Boolean =
        try {
            withClient { c -> LightController(c, config.value.connection.priority).block(c) }
            true
        } catch (e: HyperionException) {
            if (!quiet || _status.value is ConnectionStatus.Connected) _message.value = e.message
            _status.value = ConnectionStatus.Failed(e.message ?: "Fehler")
            false
        }

    private fun launchLight(success: String?, block: suspend LightController.(HyperionClient) -> Unit) {
        if (!config.value.connection.isConfigured) {
            _message.value = "Zuerst unter „Verbindung“ den Hyperion-Server eintragen"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            if (runLight(block = block)) {
                if (success != null) _message.value = success
                refreshNow()
            }
        }
    }

    private fun rememberScene(scene: Scene) {
        if (scene.action == LightAction.Off || scene.action == LightAction.HyperionDefault) return
        store.update { it.copy(lastScene = scene, userBrightness = scene.brightness ?: it.userBrightness) }
    }

    override fun onCleared() {
        discovery.stop()
        client?.close()
        super.onCleared()
    }
}
