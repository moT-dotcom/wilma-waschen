package de.kinolicht.app

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import de.kinolicht.app.ui.MainActivity
import de.kinolicht.core.AppConfig
import de.kinolicht.core.HyperionException
import de.kinolicht.core.LightAction
import de.kinolicht.core.RuleAction
import de.kinolicht.core.Scene
import de.kinolicht.core.ScheduleRule
import de.kinolicht.core.Scheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import kotlin.math.roundToInt

/**
 * Dauerhaft laufender Dienst: koppelt die LEDs an Ein/Aus des Beamers,
 * führt Zeitpläne aus und steuert Einschlaf-Timer und lange Übergänge.
 */
class LightService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var transitionJob: Job? = null
    private var powerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private val app get() = KinoLichtApp.from(this)
    private val config: AppConfig get() = app.store.config.value

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> onProjectorOn()
                Intent.ACTION_SCREEN_OFF, Intent.ACTION_SHUTDOWN -> onProjectorOff()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SHUTDOWN)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        when (intent?.action) {
            ACTION_BOOT -> onProjectorOn()
            ACTION_RULE -> intent.getStringExtra(EXTRA_ID)?.let(::runScheduledRule)
            ACTION_TEST_RULE -> intent.getStringExtra(EXTRA_RULE)
                ?.let { runCatching { AppConfig.json.decodeFromString(ScheduleRule.serializer(), it) }.getOrNull() }
                ?.let { execute(it, config) }
            ACTION_SLEEP -> startSleepTimer(intent.getIntExtra(EXTRA_MINUTES, 30))
            ACTION_CANCEL_SLEEP -> cancelTransition()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        releaseLocks()
        scope.cancel()
        super.onDestroy()
    }

    // ── Beamer an/aus ────────────────────────────────────────────────────────

    private fun onProjectorOn() {
        val cfg = config
        if (!cfg.automation.followProjector || !cfg.connection.isConfigured) return
        val night = Scheduler.isNight(cfg.automation, ZonedDateTime.now(), cfg.location)
        val sceneId = if (night) cfg.automation.nightSceneId else cfg.automation.daySceneId
        val scene = cfg.scene(sceneId) ?: cfg.lastScene
        powerJob?.cancel()
        powerJob = scope.launch {
            // Nach dem Aufwachen braucht das WLAN ein paar Sekunden.
            retrying(attempts = 8) {
                cfg.withLight { if (scene != null) applyScene(scene) else turnOn() }
            }
        }
    }

    private fun onProjectorOff() {
        cancelTransition()
        val cfg = config
        if (!cfg.automation.followProjector || !cfg.connection.isConfigured) return
        powerJob?.cancel()
        powerJob = scope.launch {
            // Schnell sein: Im Standby trennt der Beamer bald das Netzwerk.
            retrying(attempts = 2, baseDelayMs = 500) { cfg.withLight(readTimeoutMs = 1500) { turnOff() } }
        }
    }

    // ── Zeitpläne ────────────────────────────────────────────────────────────

    private fun runScheduledRule(id: String) {
        val cfg = config
        val rule = cfg.rules.firstOrNull { it.id == id }
        // Nächsten Termin gleich wieder planen, auch wenn diese Ausführung übersprungen wird.
        AlarmScheduler.rescheduleAll(this, cfg)
        if (rule == null || !rule.enabled) return
        val interactive = getSystemService(PowerManager::class.java)?.isInteractive ?: true
        if (rule.onlyWhenProjectorOn && !interactive) {
            Log.i(TAG, "Regel $id übersprungen: Beamer aus")
            return
        }
        execute(rule, cfg)
    }

    private fun execute(rule: ScheduleRule, cfg: AppConfig) {
        if (!cfg.connection.isConfigured) return
        when (val action = rule.action) {
            is RuleAction.ApplyScene -> cfg.scene(action.sceneId)?.let { scene ->
                scope.launch { retrying(4) { cfg.withLight { applyScene(scene) } }; remember(scene) }
            }
            RuleAction.TurnOn -> scope.launch { retrying(4) { cfg.withLight { turnOn() } } }
            RuleAction.TurnOff -> {
                cancelTransition()
                scope.launch { retrying(4) { cfg.withLight { turnOff() } } }
            }
            is RuleAction.FadeOut -> startTransition("Ausblenden") { report ->
                val from = currentBrightness(cfg)
                cfg.withLight { fadeOut(from, action.minutes * 60_000L, cfg.userBrightness, report) }
            }
            is RuleAction.Sunrise -> startTransition("Lichtwecker") { report ->
                cfg.withLight { sunrise(action.minutes * 60_000L, report) }
            }
            is RuleAction.FadeBrightness -> startTransition("Helligkeit") { report ->
                val from = currentBrightness(cfg)
                cfg.withLight { fadeBrightness(from, action.target, action.minutes * 60_000L, report) }
                app.store.update { it.copy(userBrightness = action.target) }
            }
        }
    }

    // ── Einschlaf-Timer und Übergänge ────────────────────────────────────────

    private fun startSleepTimer(minutes: Int) {
        val cfg = config
        val fadeMinutes = cfg.automation.sleepFadeMinutes.coerceAtMost(minutes)
        val endsAt = System.currentTimeMillis() + minutes * 60_000L
        startTransition("Einschlaf-Timer", sleepEndsAt = endsAt) { report ->
            delay((minutes - fadeMinutes) * 60_000L)
            val from = currentBrightness(cfg)
            cfg.withLight { fadeOut(from, fadeMinutes * 60_000L, cfg.userBrightness, report) }
        }
    }

    private fun startTransition(label: String, sleepEndsAt: Long? = null, block: suspend ((Double) -> Unit) -> Unit) {
        cancelTransition()
        acquireLocks()
        LightRuntime.sleepEndsAt.value = sleepEndsAt
        LightRuntime.task.value = label
        transitionJob = scope.launch {
            try {
                block { progress -> LightRuntime.task.value = "$label ${(progress * 100).roundToInt()} %" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HyperionException) {
                Log.w(TAG, "$label abgebrochen: ${e.message}")
            } finally {
                LightRuntime.task.value = null
                LightRuntime.sleepEndsAt.value = null
                releaseLocks()
            }
        }
    }

    private fun cancelTransition() {
        transitionJob?.cancel()
        transitionJob = null
        LightRuntime.task.value = null
        LightRuntime.sleepEndsAt.value = null
    }

    private suspend fun currentBrightness(cfg: AppConfig): Int =
        runCatching { cfg.withLight { it.serverInfo().brightness } }.getOrNull() ?: cfg.userBrightness

    private fun remember(scene: Scene) {
        if (scene.action == LightAction.Off) return
        app.store.update { it.copy(lastScene = scene, userBrightness = scene.brightness ?: it.userBrightness) }
    }

    private suspend fun retrying(attempts: Int, baseDelayMs: Long = 2000, block: suspend () -> Unit) {
        repeat(attempts) { attempt ->
            try {
                block()
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: HyperionException) {
                Log.w(TAG, "Versuch ${attempt + 1}/$attempts: ${e.message}")
                if (attempt < attempts - 1) delay((baseDelayMs * (attempt + 1)).coerceAtMost(8000))
            }
        }
    }

    // ── Vordergrund-Benachrichtigung und Sperren ─────────────────────────────

    private fun startInForeground() {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, KinoLichtApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Kino-Licht")
            .setContentText("LEDs folgen dem Beamer, Zeitpläne aktiv")
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @SuppressLint("WakelockTimeout")
    @Suppress("DEPRECATION")
    private fun acquireLocks() {
        if (wakeLock?.isHeld != true) {
            wakeLock = getSystemService(PowerManager::class.java)
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "kinolicht:transition")
                ?.apply { acquire(3 * 60 * 60 * 1000L) }
        }
        if (wifiLock?.isHeld != true) {
            wifiLock = applicationContext.getSystemService(WifiManager::class.java)
                ?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "kinolicht:transition")
                ?.apply { acquire() }
        }
    }

    private fun releaseLocks() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        runCatching { if (wifiLock?.isHeld == true) wifiLock?.release() }
    }

    companion object {
        private const val TAG = "KinoLicht"
        private const val NOTIFICATION_ID = 1
        const val ACTION_BOOT = "de.kinolicht.BOOT"
        const val ACTION_RULE = "de.kinolicht.RULE"
        const val ACTION_SLEEP = "de.kinolicht.SLEEP"
        const val ACTION_CANCEL_SLEEP = "de.kinolicht.CANCEL_SLEEP"
        const val ACTION_TEST_RULE = "de.kinolicht.TEST_RULE"
        const val EXTRA_ID = "id"
        const val EXTRA_RULE = "rule"
        const val EXTRA_MINUTES = "minutes"

        fun start(context: Context, action: String? = null, configure: Intent.() -> Unit = {}) {
            val intent = Intent(context, LightService::class.java).setAction(action).apply(configure)
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure { Log.w(TAG, "Dienst konnte nicht starten: ${it.message}") }
        }
    }
}
