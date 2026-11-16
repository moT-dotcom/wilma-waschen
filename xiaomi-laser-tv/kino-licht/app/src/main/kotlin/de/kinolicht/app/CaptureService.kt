package de.kinolicht.app

import android.app.Activity
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import de.kinolicht.app.ui.MainActivity
import de.kinolicht.core.BlackFrameDetector
import de.kinolicht.core.Frame
import de.kinolicht.core.HyperionException
import de.kinolicht.core.LightController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Bild-Sync: nimmt den Bildschirm des Beamers stark verkleinert auf und schickt
 * die Bilder an Hyperion, das daraus die LED-Farben berechnet.
 *
 * Android gibt dabei nur heraus, was es selbst zeichnet. HDMI-Eingänge und
 * kopiergeschützte Apps laufen meist über eine eigene Videoebene und bleiben
 * in der Aufnahme schwarz – das zählt der Dienst mit und zeigt es in der App.
 */
class CaptureService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.i(TAG, "Aufnahme vom System beendet")
            stopSelf()
        }
    }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START) {
            stopSelf()
            return START_NOT_STICKY
        }
        val data = IntentCompat.getParcelableExtra(intent, EXTRA_DATA, Intent::class.java)
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        if (data == null || resultCode != Activity.RESULT_OK) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            // Ab Android 14 muss der Dienst im Vordergrund sein, bevor die Aufnahme beginnt.
            startInForeground()
            begin(resultCode, data)
        } catch (e: Exception) {
            Log.w(TAG, "Aufnahme konnte nicht starten", e)
            LightRuntime.capture.value = CaptureStatus(error = "Aufnahme konnte nicht starten: ${e.message}")
            stopSelf()
        }
        // Die Aufnahme-Erlaubnis gilt nur für diesen Start, ein Neustart durch das System wäre nutzlos.
        return START_NOT_STICKY
    }

    private fun begin(resultCode: Int, data: Intent) {
        release()
        val handlerThread = HandlerThread("kinolicht-capture").also { it.start() }
        val handler = Handler(handlerThread.looper)
        val mp = getSystemService(MediaProjectionManager::class.java).getMediaProjection(resultCode, data)
            ?: error("keine Aufnahme-Erlaubnis")
        mp.registerCallback(projectionCallback, handler)
        val imageReader = ImageReader.newInstance(WIDTH, HEIGHT, PixelFormat.RGBA_8888, 2)
        thread = handlerThread
        projection = mp
        reader = imageReader
        display = mp.createVirtualDisplay(
            "Kino-Licht Bild-Sync", WIDTH, HEIGHT, resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.surface, null, handler,
        )
        ContextCompat.registerReceiver(
            this, screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_EXPORTED,
        )
        job = scope.launch { stream(imageReader) }
    }

    private suspend fun stream(imageReader: ImageReader) {
        val cfg = KinoLichtApp.from(this).store.config.value
        val appPriority = cfg.connection.priority
        val priority = Frame.syncPriority(appPriority)
        val interval = 1000L / cfg.syncFps.coerceIn(1, 30)
        val detector = BlackFrameDetector()
        val client = cfg.connection.newClient(readTimeoutMs = 2000)
        var status = CaptureStatus(running = true)
        LightRuntime.capture.value = status
        try {
            // Szenen der App liegen vor dem Sync – Platz machen, damit das Bild sichtbar wird.
            runCatching {
                LightController(client, appPriority).turnOn()
                client.clear(appPriority)
            }
            var last: ByteArray? = null
            var lastSent = 0L
            var sentInWindow = 0
            var windowStart = SystemClock.elapsedRealtime()
            var luma = 0
            while (coroutineContext.isActive) {
                val started = SystemClock.elapsedRealtime()
                val fresh = readFrame(imageReader)
                if (fresh != null) {
                    last = fresh
                    luma = Frame.averageLuma(fresh)
                }
                // Steht das Bild, kommt nichts Neues: einmal pro Sekunde wiederholen, damit es nicht verfällt.
                val frame = fresh ?: last?.takeIf { started - lastSent >= 1000 }
                if (frame != null) {
                    try {
                        client.setImage(WIDTH, HEIGHT, frame, priority, "Kino-Licht Bild-Sync", FRAME_LIFETIME_MS)
                        lastSent = started
                        sentInWindow++
                        if (status.error != null) status = status.copy(error = null)
                    } catch (e: HyperionException) {
                        status = status.copy(error = e.message, fps = 0)
                        LightRuntime.capture.value = status
                        delay(2000)
                    }
                }
                val now = SystemClock.elapsedRealtime()
                if (now - windowStart >= 1000) {
                    val black = detector.update(luma, now)
                    status = status.copy(
                        fps = sentInWindow,
                        black = black,
                        seconds = status.seconds + 1,
                        blackSeconds = status.blackSeconds + if (luma <= BLACK_LUMA) 1 else 0,
                    )
                    LightRuntime.capture.value = status
                    last?.let { LightRuntime.capturePreview.value = toBitmap(it) }
                    sentInWindow = 0
                    windowStart = now
                }
                delay((interval - (SystemClock.elapsedRealtime() - started)).coerceAtLeast(1))
            }
        } finally {
            withContext(NonCancellable) {
                runCatching { client.clear(priority) }
                client.close()
            }
        }
    }

    private fun readFrame(imageReader: ImageReader): ByteArray? {
        val image = runCatching { imageReader.acquireLatestImage() }.getOrNull() ?: return null
        return try {
            val plane = image.planes[0]
            Frame.rgbaToRgb(plane.buffer, WIDTH, HEIGHT, plane.rowStride, plane.pixelStride)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Bild nicht lesbar: ${e.message}")
            null
        } finally {
            image.close()
        }
    }

    private fun toBitmap(rgb: ByteArray): Bitmap {
        val pixels = IntArray(WIDTH * HEIGHT)
        for (i in pixels.indices) {
            val r = rgb[i * 3].toInt() and 0xFF
            val g = rgb[i * 3 + 1].toInt() and 0xFF
            val b = rgb[i * 3 + 2].toInt() and 0xFF
            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(pixels, WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
    }

    private fun release() {
        job?.cancel()
        job = null
        runCatching { unregisterReceiver(screenOffReceiver) }
        runCatching { display?.release() }
        runCatching { reader?.close() }
        runCatching { projection?.unregisterCallback(projectionCallback) }
        runCatching { projection?.stop() }
        thread?.quitSafely()
        display = null
        reader = null
        projection = null
        thread = null
    }

    override fun onDestroy() {
        release()
        // Der laufende Job räumt die Hyperion-Priorität in seinem finally-Block auf.
        scope.launch { delay(1500); scope.cancel() }
        LightRuntime.capture.value = LightRuntime.capture.value.copy(running = false, fps = 0, black = false)
        super.onDestroy()
    }

    private fun startInForeground() {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, KinoLichtApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Kino-Licht Bild-Sync")
            .setContentText("LEDs folgen dem Bild")
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    companion object {
        private const val TAG = "KinoLichtCapture"
        private const val NOTIFICATION_ID = 2
        private const val ACTION_START = "de.kinolicht.CAPTURE_START"
        private const val EXTRA_RESULT_CODE = "resultCode"
        private const val EXTRA_DATA = "data"

        /** Für LEDs reicht ein winziges Bild; klein hält Netzwerk und CPU des Beamers frei. */
        const val WIDTH = 96
        const val HEIGHT = 54
        private const val FRAME_LIFETIME_MS = 5000
        private const val BLACK_LUMA = 4

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, CaptureService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_DATA, data)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CaptureService::class.java))
        }
    }
}
