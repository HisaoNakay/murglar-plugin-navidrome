package com.badmanners.murglar.lib.navidrome.api

import com.badmanners.murglar.lib.core.localization.MessageException
import com.badmanners.murglar.lib.core.log.LoggerMiddleware
import com.badmanners.murglar.lib.core.network.NetworkMiddleware
import com.badmanners.murglar.lib.core.network.NetworkRequest
import com.badmanners.murglar.lib.core.network.ResponseConverters
import com.badmanners.murglar.lib.core.preference.PreferenceMiddleware
import com.badmanners.murglar.lib.core.utils.MurglarLibUtils.toMD5HexString
import com.badmanners.murglar.lib.core.utils.MurglarLibUtils.urlEncode
import com.badmanners.murglar.lib.core.utils.getIntOpt
import com.badmanners.murglar.lib.core.utils.getJsonObjectOpt
import com.badmanners.murglar.lib.core.utils.getStringOpt
import com.badmanners.murglar.lib.navidrome.localization.NavidromeMessages
import kotlinx.serialization.json.JsonObject
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URI
import java.security.SecureRandom
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore


/**
 * NB: host app doesn't provide kotlinx.coroutines to plugins (it's minified away), so only java concurrency here.
 *
 * Subsonic API client, that survives server IP changes (e.g. laptop on the phone's hotspot):
 * when the saved address stops answering, it scans local /24 networks of this device for the server.
 */
class SubsonicClient(
    private val preferences: PreferenceMiddleware,
    private val network: NetworkMiddleware,
    private val logger: LoggerMiddleware,
    private val messages: NavidromeMessages
) {

    companion object {
        private const val TAG = "Navidrome"

        private const val API_VERSION = "1.16.1"
        private const val CLIENT_NAME = "murglar"
        private const val DEFAULT_PORT = 4533

        private const val SERVER_URL_PREFERENCE = "server_url"
        private const val USERNAME_PREFERENCE = "username"
        private const val TOKEN_PREFERENCE = "token"
        private const val SALT_PREFERENCE = "salt"
        private const val AUTO_DISCOVERY_PREFERENCE = "auto_discovery"

        private const val CHECK_INTERVAL_MS = 60_000L
        private const val PROBE_TIMEOUT_MS = 700
        private const val SCAN_TIMEOUT_MS = 300
        private const val SCAN_PARALLELISM = 64

        private val MOBILE_INTERFACE_PREFIXES = listOf("rmnet", "ccmni", "pdp", "dummy", "tun", "clat", "v4-")

        private val ERROR_CODES_INVALID_CREDENTIALS = setOf(40, 41, 44)

        /**
         * `192.168.1.10` -> `http://192.168.1.10:4533`, `https://music.example.com/` -> `https://music.example.com`
         */
        fun normalizeServerUrl(input: String): String {
            val trimmed = input.trim().trimEnd('/')
            require(trimmed.isNotEmpty()) { "Empty server address!" }
            val hasScheme = trimmed.contains("://")
            val uri = URI(if (hasScheme) trimmed else "http://$trimmed")
            val host = requireNotNull(uri.host) { "Invalid server address '$input'!" }
            val port = when {
                uri.port != -1 -> uri.port
                !hasScheme -> DEFAULT_PORT
                else -> -1
            }
            return buildString {
                append(uri.scheme).append("://").append(host)
                if (port != -1)
                    append(':').append(port)
                append(uri.rawPath.orEmpty().trimEnd('/'))
            }
        }

        private fun URI.effectivePort() = when {
            port != -1 -> port
            scheme == "https" -> 443
            else -> 80
        }

        private fun Throwable.isNetworkError() = generateSequence(this) { it.cause }.any {
            it is IOException || it.javaClass.name.contains("Timeout")
        }
    }

    val serverUrl: String
        get() = preferences.getString(SERVER_URL_PREFERENCE, "")

    val username: String
        get() = preferences.getString(USERNAME_PREFERENCE, "")

    private val token: String
        get() = preferences.getString(TOKEN_PREFERENCE, "")

    private val salt: String
        get() = preferences.getString(SALT_PREFERENCE, "")

    val hasCredentials: Boolean
        get() = serverUrl.isNotEmpty() && username.isNotEmpty() && token.isNotEmpty()

    var autoDiscovery: Boolean
        get() = preferences.getBoolean(AUTO_DISCOVERY_PREFERENCE, true)
        set(value) = preferences.setBoolean(AUTO_DISCOVERY_PREFERENCE, value)

    @Volatile
    private var lastSuccessMs = 0L

    private val discoveryLock = Semaphore(1)


    /**
     * Saves credentials and checks them. If the server isn't reachable at [serverAddress] - tries to find it.
     */
    suspend fun login(serverAddress: String, username: String, password: String) {
        val url = normalizeServerUrl(serverAddress)
        val salt = randomSalt()
        preferences.setString(SERVER_URL_PREFERENCE, url)
        preferences.setString(USERNAME_PREFERENCE, username)
        preferences.setString(SALT_PREFERENCE, salt)
        preferences.setString(TOKEN_PREFERENCE, (password + salt).toByteArray().toMD5HexString())
        lastSuccessMs = 0L
        call("ping")
    }

    fun logout() {
        preferences.remove(USERNAME_PREFERENCE)
        preferences.remove(TOKEN_PREFERENCE)
        preferences.remove(SALT_PREFERENCE)
        lastSuccessMs = 0L
    }

    fun changeServerUrl(serverAddress: String) {
        preferences.setString(SERVER_URL_PREFERENCE, normalizeServerUrl(serverAddress))
        lastSuccessMs = 0L
    }

    /**
     * Calls the API method and returns the `subsonic-response` object.
     */
    suspend fun call(method: String, vararg params: Pair<String, Any>): JsonObject {
        ensureReachable()
        val usedUrl = serverUrl
        return try {
            callAt(usedUrl, method, params.asList())
        } catch (e: Exception) {
            if (!e.isNetworkError() || !autoDiscovery)
                throw e
            logger.w(TAG, "Request to $usedUrl failed, searching for server", e)
            discover(usedUrl) ?: throw e
            callAt(serverUrl, method, params.asList())
        }
    }

    /**
     * Url for direct use by the player/image loader (stream, getCoverArt), built for the current server address.
     */
    fun buildUrl(method: String, vararg params: Pair<String, Any>): String =
        (authParams() + params).joinToString("&", "$serverUrl/rest/$method?") {
            "${it.first.urlEncode()}=${it.second.toString().urlEncode()}"
        }

    /**
     * Checks the current address (no more often than once per [CHECK_INTERVAL_MS]) and searches for the server
     * if the address doesn't answer.
     */
    suspend fun ensureReachable() {
        if (System.currentTimeMillis() - lastSuccessMs < CHECK_INTERVAL_MS)
            return
        val url = serverUrl.takeIf { it.isNotEmpty() } ?: return
        val uri = URI(url)
        if (probe(uri.host, uri.effectivePort(), PROBE_TIMEOUT_MS)) {
            lastSuccessMs = System.currentTimeMillis()
            return
        }
        if (autoDiscovery)
            discover(url)
    }

    /**
     * Scans local networks for the server on the same port as [failedUrl].
     *
     * @return new server url (it's also saved) or null if nothing found.
     */
    suspend fun discover(failedUrl: String = serverUrl): String? {
        discoveryLock.acquire()
        try {
            return discoverLocked(failedUrl)
        } finally {
            discoveryLock.release()
        }
    }

    private suspend fun discoverLocked(failedUrl: String): String? {
        // address was already changed by a concurrent discovery
        serverUrl.takeIf { it != failedUrl && it.isNotEmpty() }?.let { return it }

        val current = URI(failedUrl)
        val scheme = current.scheme ?: "http"
        val port = current.effectivePort()
        val path = current.rawPath.orEmpty()

        val candidates = candidateHosts(current.host)
        logger.i(TAG, "Discovery: scanning ${candidates.size} hosts on port $port")
        val openHosts = scan(candidates, port)
        logger.i(TAG, "Discovery: port $port is open on $openHosts")

        for (host in openHosts) {
            val url = "$scheme://$host:$port$path"
            val isServer = try {
                callAt(url, "ping", emptyList())
                true
            } catch (e: Exception) {
                logger.d(TAG, "Discovery: $url isn't our server", e)
                false
            }
            if (isServer) {
                logger.i(TAG, "Discovery: server found at $url")
                preferences.setString(SERVER_URL_PREFERENCE, url)
                return url
            }
        }
        return null
    }

    private suspend fun callAt(baseUrl: String, method: String, params: List<Pair<String, Any>>): JsonObject {
        val request = NetworkRequest.Builder("$baseUrl/rest/$method")
            .apply { (authParams() + params).forEach { addParameter(it.first, it.second) } }
            .build()
        val result = network.execute(request, ResponseConverters.asJsonObject()).result
        val response = result.getJsonObjectOpt("subsonic-response")
            ?: throw MessageException(messages.illegalResponseFormat)

        if (response.getStringOpt("status") != "ok") {
            val error = response.getJsonObjectOpt("error")
            if (error?.getIntOpt("code")?.let { it in ERROR_CODES_INVALID_CREDENTIALS } == true)
                throw MessageException(messages.invalidCredentials)
            val message = error?.getStringOpt("message") ?: messages.anErrorHasOccurred
            throw MessageException("${messages.serviceName}: $message")
        }

        lastSuccessMs = System.currentTimeMillis()
        return response
    }

    private fun authParams(): List<Pair<String, Any>> = listOf(
        "u" to username,
        "t" to token,
        "s" to salt,
        "v" to API_VERSION,
        "c" to CLIENT_NAME,
        "f" to "json"
    )

    private fun randomSalt(): String {
        val bytes = ByteArray(8).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Last known host first, then all hosts of the /24 networks of this device's local IPv4 addresses.
     */
    private fun candidateHosts(lastHost: String?): List<String> {
        val hosts = LinkedHashSet<String>()
        lastHost?.takeIf { it.matches("\\d+\\.\\d+\\.\\d+\\.\\d+".toRegex()) }?.let(hosts::add)

        val interfaces = try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        } catch (e: Exception) {
            logger.w(TAG, "Discovery: can't list network interfaces", e)
            emptyList()
        }

        val ownAddresses = interfaces
            .filter { iface ->
                runCatching { iface.isUp && !iface.isLoopback }.getOrDefault(false) &&
                    MOBILE_INTERFACE_PREFIXES.none { iface.name.startsWith(it) }
            }
            .flatMap { it.interfaceAddresses }
            .mapNotNull { it.address as? Inet4Address }
            .filter { it.isSiteLocalAddress }

        for (address in ownAddresses) {
            val bytes = address.address.map { it.toInt() and 0xFF }
            val prefix = "${bytes[0]}.${bytes[1]}.${bytes[2]}."
            (1..254).filter { it != bytes[3] }.forEach { hosts += "$prefix$it" }
        }
        return hosts.toList()
    }

    private fun scan(hosts: List<String>, port: Int): List<String> {
        if (hosts.isEmpty())
            return emptyList()
        val executor = Executors.newFixedThreadPool(minOf(SCAN_PARALLELISM, hosts.size))
        try {
            val tasks = hosts.map { host -> Callable { host.takeIf { probe(it, port, SCAN_TIMEOUT_MS) } } }
            return executor.invokeAll(tasks).mapNotNull { it.get() }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun probe(host: String, port: Int, timeoutMs: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
        true
    } catch (_: Exception) {
        false
    }
}
