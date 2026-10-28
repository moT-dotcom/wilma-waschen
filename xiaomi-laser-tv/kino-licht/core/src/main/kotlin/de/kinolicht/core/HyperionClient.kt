package de.kinolicht.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.BufferedReader
import java.io.Closeable
import java.io.IOException
import java.io.Writer
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException

open class HyperionException(message: String) : Exception(message)
class HyperionAuthException(message: String) : HyperionException(message)

/** Die Befehle, die Kino-Licht an Hyperion schickt. */
interface HyperionApi {
    suspend fun serverInfo(): HyperionState
    suspend fun setColor(color: Rgb, priority: Int)
    suspend fun setEffect(name: String, priority: Int)
    suspend fun setImage(width: Int, height: Int, rgb: ByteArray, priority: Int, name: String)
    suspend fun clear(priority: Int)
    suspend fun setComponent(component: String, enabled: Boolean)
    suspend fun setBrightness(brightness: Int)
}

/**
 * Client für die JSON-API von Hyperion.ng über TCP (Port 19444).
 * Jede Nachricht ist eine JSON-Zeile; Antworten werden über "tan" zugeordnet.
 * Die Verbindung bleibt offen und wird bei Bedarf neu aufgebaut.
 */
class HyperionClient(
    val host: String,
    val port: Int = DEFAULT_PORT,
    private val token: String? = null,
    private val instance: Int = 0,
    private val origin: String = ORIGIN,
    private val connectTimeoutMs: Int = 3000,
    private val readTimeoutMs: Int = 5000,
) : HyperionApi, Closeable {

    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: Writer? = null
    private var tan = 0
    private val mutex = Mutex()

    private val isOpen: Boolean get() = socket?.let { it.isConnected && !it.isClosed } ?: false

    suspend fun request(command: JsonObject, timeoutMs: Int = readTimeoutMs): JsonObject = mutex.withLock {
        withContext(Dispatchers.IO) {
            val reused = isOpen
            try {
                openLocked()
                sendLocked(command, timeoutMs)
            } catch (e: IOException) {
                closeLocked()
                // Eine lange offene Verbindung kann inzwischen tot sein: einmal neu versuchen.
                if (!reused) throw HyperionException(describe(e))
                try {
                    openLocked()
                    sendLocked(command, timeoutMs)
                } catch (retry: IOException) {
                    closeLocked()
                    throw HyperionException(describe(retry))
                }
            }
        }
    }

    suspend fun sysInfo(): Pair<String?, String?> {
        val info = request(cmd("sysinfo"))["info"]?.jsonObject
        val version = info?.get("hyperion")?.jsonObject?.get("version")?.jsonPrimitive?.contentOrNull
        val hostname = info?.get("system")?.jsonObject?.get("hostName")?.jsonPrimitive?.contentOrNull
        return version to hostname
    }

    /** Server-Info plus Version und Hostname. */
    suspend fun fetchState(): HyperionState {
        val state = serverInfo()
        val (version, hostname) = runCatching { sysInfo() }.getOrDefault(null to null)
        return state.copy(version = version, hostname = hostname)
    }

    suspend fun isTokenRequired(): Boolean {
        val resp = request(buildJsonObject { put("command", "authorize"); put("subcommand", "tokenRequired") })
        return resp["info"]?.jsonObject?.get("required")?.jsonPrimitive?.booleanOrNull ?: false
    }

    /**
     * Fordert ein Token an. Hyperion zeigt die Anfrage in der Weboberfläche,
     * die Antwort kommt erst, wenn sie dort bestätigt wurde.
     */
    suspend fun requestToken(comment: String, id: String): String {
        val resp = request(
            buildJsonObject {
                put("command", "authorize")
                put("subcommand", "requestToken")
                put("comment", comment)
                put("id", id)
            },
            timeoutMs = 180_000,
        )
        return resp["info"]?.jsonObject?.get("token")?.jsonPrimitive?.contentOrNull
            ?: throw HyperionException("Hyperion hat kein Token geliefert")
    }

    override suspend fun serverInfo(): HyperionState {
        val info = request(cmd("serverinfo"))["info"]?.jsonObject
            ?: throw HyperionException("Leere Server-Info")
        return HyperionState.parse(info)
    }

    override suspend fun setColor(color: Rgb, priority: Int) {
        request(buildJsonObject {
            put("command", "color")
            putJsonArray("color") { add(color.r); add(color.g); add(color.b) }
            put("priority", priority)
            put("origin", origin)
        })
    }

    override suspend fun setEffect(name: String, priority: Int) {
        request(buildJsonObject {
            put("command", "effect")
            putJsonObject("effect") { put("name", name) }
            put("priority", priority)
            put("origin", origin)
        })
    }

    override suspend fun setImage(width: Int, height: Int, rgb: ByteArray, priority: Int, name: String) {
        require(rgb.size == width * height * 3) { "Bildgröße passt nicht zu ${width}x$height" }
        request(buildJsonObject {
            put("command", "image")
            put("imagewidth", width)
            put("imageheight", height)
            put("imagedata", Base64.encode(rgb))
            put("priority", priority)
            put("origin", origin)
            put("name", name)
        })
    }

    override suspend fun clear(priority: Int) {
        request(buildJsonObject { put("command", "clear"); put("priority", priority) })
    }

    override suspend fun setComponent(component: String, enabled: Boolean) {
        request(buildJsonObject {
            put("command", "componentstate")
            putJsonObject("componentstate") {
                put("component", component)
                put("state", enabled)
            }
        })
    }

    override suspend fun setBrightness(brightness: Int) {
        request(buildJsonObject {
            put("command", "adjustment")
            putJsonObject("adjustment") { put("brightness", brightness.coerceIn(0, 100)) }
        })
    }

    override fun close() {
        closeLocked()
    }

    private fun openLocked() {
        if (isOpen) return
        val s = Socket()
        s.tcpNoDelay = true
        s.connect(InetSocketAddress(host, port), connectTimeoutMs)
        s.soTimeout = readTimeoutMs
        socket = s
        reader = s.getInputStream().bufferedReader(Charsets.UTF_8)
        writer = s.getOutputStream().bufferedWriter(Charsets.UTF_8)
        if (!token.isNullOrBlank()) {
            sendLocked(buildJsonObject {
                put("command", "authorize")
                put("subcommand", "login")
                put("token", token)
            }, readTimeoutMs)
        }
        if (instance != 0) {
            sendLocked(buildJsonObject {
                put("command", "instance")
                put("subcommand", "switchTo")
                put("instance", instance)
            }, readTimeoutMs)
        }
    }

    private fun sendLocked(command: JsonObject, timeoutMs: Int): JsonObject {
        val myTan = ++tan
        val payload = JsonObject(command + ("tan" to JsonPrimitive(myTan)))
        val w = writer ?: throw IOException("nicht verbunden")
        val r = reader ?: throw IOException("nicht verbunden")
        socket?.soTimeout = timeoutMs
        w.write(payload.toString())
        w.write("\n")
        w.flush()
        val sent = command["command"]?.jsonPrimitive?.contentOrNull.orEmpty()
        while (true) {
            val line = r.readLine() ?: throw IOException("Hyperion hat die Verbindung beendet")
            if (line.isBlank()) continue
            val resp = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull() ?: continue
            val respTan = resp["tan"]?.jsonPrimitive?.intOrNull
            val respCommand = resp["command"]?.jsonPrimitive?.contentOrNull.orEmpty()
            // Abo-Updates und Antworten auf ältere Anfragen überspringen.
            val matches = respTan == myTan || (respTan == null && respCommand.startsWith(sent))
            if (!matches) continue
            if (resp["success"]?.jsonPrimitive?.booleanOrNull == false) {
                val error = resp["error"]?.jsonPrimitive?.contentOrNull ?: "Hyperion meldet einen Fehler"
                if (error.contains("authoriz", ignoreCase = true) || error.contains("token", ignoreCase = true)) {
                    throw HyperionAuthException(error)
                }
                throw HyperionException(error)
            }
            return resp
        }
    }

    private fun closeLocked() {
        runCatching { socket?.close() }
        socket = null
        reader = null
        writer = null
    }

    private fun describe(e: IOException): String = when (e) {
        is UnknownHostException -> "Adresse „$host“ unbekannt"
        is ConnectException -> "Hyperion unter $host:$port nicht erreichbar"
        is SocketTimeoutException -> "Keine Antwort von $host:$port"
        else -> e.message ?: "Verbindungsfehler"
    }

    private fun cmd(name: String) = buildJsonObject { put("command", name) }

    companion object {
        const val DEFAULT_PORT = 19444
        const val ORIGIN = "Kino-Licht"
        private val json = Json { ignoreUnknownKeys = true }
    }
}

/** Minimaler Base64-Encoder, damit der Kern ohne android.util/java.util.Base64 auskommt. */
internal object Base64 {
    private const val CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    fun encode(data: ByteArray): String {
        val out = StringBuilder((data.size + 2) / 3 * 4)
        var i = 0
        while (i < data.size) {
            val b0 = data[i].toInt() and 0xFF
            val b1 = if (i + 1 < data.size) data[i + 1].toInt() and 0xFF else 0
            val b2 = if (i + 2 < data.size) data[i + 2].toInt() and 0xFF else 0
            out.append(CHARS[b0 shr 2])
            out.append(CHARS[((b0 and 0x03) shl 4) or (b1 shr 4)])
            out.append(if (i + 1 < data.size) CHARS[((b1 and 0x0F) shl 2) or (b2 shr 6)] else '=')
            out.append(if (i + 2 < data.size) CHARS[b2 and 0x3F] else '=')
            i += 3
        }
        return out.toString()
    }
}
