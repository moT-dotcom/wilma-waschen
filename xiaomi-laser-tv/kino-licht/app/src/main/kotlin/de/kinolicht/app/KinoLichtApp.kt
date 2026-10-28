package de.kinolicht.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import de.kinolicht.core.AppConfig
import de.kinolicht.core.ConnectionSettings
import de.kinolicht.core.HyperionClient
import de.kinolicht.core.LightController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KinoLichtApp : Application() {

    lateinit var store: ConfigStore
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        store = ConfigStore(this)
        createNotificationChannel()
        LightService.start(this)
        // Zeitpläne neu planen, sobald sich Regeln, Szenen oder Standort ändern.
        appScope.launch {
            store.config
                .map { Triple(it.rules, it.location, it.scenes.map { s -> s.id }) }
                .distinctUntilChanged()
                .collect { AlarmScheduler.rescheduleAll(this@KinoLichtApp, store.config.value) }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Kino-Licht Dienst", NotificationManager.IMPORTANCE_MIN).apply {
            description = "Hält die Beamer-Kopplung und Zeitpläne aktiv"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "kinolicht_service"

        fun from(context: Context): KinoLichtApp = context.applicationContext as KinoLichtApp
    }
}

/** Konfiguration als JSON in den SharedPreferences, für UI und Dienst gemeinsam. */
class ConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("kinolicht", Context.MODE_PRIVATE)
    private val _config = MutableStateFlow(AppConfig.fromJson(prefs.getString(KEY, null)))
    val config: StateFlow<AppConfig> = _config.asStateFlow()

    @Synchronized
    fun update(transform: (AppConfig) -> AppConfig) {
        val next = transform(_config.value)
        if (next == _config.value) return
        _config.value = next
        prefs.edit().putString(KEY, next.toJson()).apply()
    }

    private companion object {
        const val KEY = "config"
    }
}

/** Zustand laufender Abläufe (Timer, Übergänge), den Dienst und UI teilen. */
object LightRuntime {
    /** Endzeitpunkt des Einschlaf-Timers in ms seit Epoch, oder null. */
    val sleepEndsAt = MutableStateFlow<Long?>(null)

    /** Beschreibung eines laufenden Übergangs, z. B. „Lichtwecker 40 %“. */
    val task = MutableStateFlow<String?>(null)
}

fun ConnectionSettings.newClient(readTimeoutMs: Int = 5000) =
    HyperionClient(host = host.trim(), port = port, token = token, instance = instance, readTimeoutMs = readTimeoutMs)

/** Öffnet eine kurze Verbindung, führt [block] aus und schließt sie wieder. */
suspend fun <T> AppConfig.withLight(readTimeoutMs: Int = 5000, block: suspend LightController.(HyperionClient) -> T): T =
    withContext(Dispatchers.IO) {
        connection.newClient(readTimeoutMs).use { client ->
            LightController(client, connection.priority).block(client)
        }
    }
