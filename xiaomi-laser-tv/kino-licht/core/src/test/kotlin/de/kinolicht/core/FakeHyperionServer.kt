package de.kinolicht.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.Closeable
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import kotlin.concurrent.thread

/**
 * Minimaler Hyperion-JSON-Server für Tests: speichert jede Anfrage und
 * antwortet zeilenweise mit passender "tan". Vor jeder Antwort kommt ein
 * Abo-Update, damit der Client lernt, fremde Nachrichten zu überspringen.
 */
class FakeHyperionServer(
    private val serverInfo: String = SERVER_INFO,
    private val requiredToken: String? = null,
) : Closeable {
    private val server = ServerSocket(0)
    val port: Int get() = server.localPort
    val requests: MutableList<JsonObject> = Collections.synchronizedList(mutableListOf())
    private val clients = Collections.synchronizedList(mutableListOf<Socket>())
    private val json = Json { ignoreUnknownKeys = true }
    private val compactInfo = json.parseToJsonElement(serverInfo).toString()

    init {
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                clients += socket
                thread(isDaemon = true) { serve(socket) }
            }
        }
    }

    /** Trennt alle offenen Verbindungen, als wäre Hyperion neu gestartet. */
    fun dropConnections() {
        clients.toList().forEach { runCatching { it.close() } }
        clients.clear()
    }

    private fun serve(socket: Socket) = runCatching {
        var loggedIn = requiredToken == null
        val reader = socket.getInputStream().bufferedReader()
        val writer = socket.getOutputStream().bufferedWriter()
        while (true) {
            val line = reader.readLine() ?: break
            val req = json.parseToJsonElement(line).jsonObject
            requests += req
            val command = req["command"]!!.jsonPrimitive.content
            val sub = req["subcommand"]?.jsonPrimitive?.contentOrNull
            val tan = req["tan"]
            val full = if (sub != null) "$command-$sub" else command
            writer.write("""{"command":"priorities-update","data":{"priorities":[]}}""" + "\n")
            val response = when {
                full == "authorize-login" -> {
                    loggedIn = req["token"]?.jsonPrimitive?.content == requiredToken
                    if (loggedIn) ok(full, tan) else fail(full, tan, "No Authorization")
                }
                full == "authorize-tokenRequired" ->
                    """{"command":"$full","success":true,"tan":$tan,"info":{"required":${requiredToken != null}}}"""
                !loggedIn -> fail(full, tan, "No Authorization")
                command == "serverinfo" -> """{"command":"serverinfo","success":true,"tan":$tan,"info":$compactInfo}"""
                command == "sysinfo" ->
                    """{"command":"sysinfo","success":true,"tan":$tan,"info":{"hyperion":{"version":"2.0.16"},"system":{"hostName":"pi"}}}"""
                command == "effect" && req["effect"]!!.jsonObject["name"]!!.jsonPrimitive.content == "Gibt es nicht" ->
                    fail(full, tan, "Effect not found")
                else -> ok(full, tan)
            }
            writer.write(response + "\n")
            writer.flush()
        }
    }

    private fun ok(command: String, tan: Any?) = """{"command":"$command","success":true,"tan":$tan}"""
    private fun fail(command: String, tan: Any?, error: String) =
        """{"command":"$command","success":false,"tan":$tan,"error":"$error"}"""

    override fun close() {
        dropConnections()
        server.close()
    }

    companion object {
        val SERVER_INFO = """
            {
              "effects": [{"name": "Rainbow swirl"}, {"name": "Candle"}, {"name": "Mein Effekt"}],
              "components": [
                {"name": "ALL", "enabled": true},
                {"name": "LEDDEVICE", "enabled": false},
                {"name": "SMOOTHING", "enabled": true}
              ],
              "adjustment": [{"brightness": 80, "id": "default"}],
              "priorities": [
                {"priority": 50, "componentId": "COLOR", "origin": "Kino-Licht@192.168.1.20", "active": true,
                 "visible": true, "value": {"RGB": [255, 120, 0], "HSL": [28, 1.0, 0.5]}},
                {"priority": 240, "componentId": "V4L", "origin": "System", "active": true, "visible": false}
              ],
              "instance": [{"instance": 0, "running": true, "friendly_name": "Wohnzimmer"}],
              "leds": [{}, {}, {}, {}]
            }
        """.trimIndent()

        fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    }
}
