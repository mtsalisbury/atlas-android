package io.nekohasekai.sfa.atlas

import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Talks to the real Atlas backend (dashboard.layer9i.com) directly --
 * no Tailscale, no local mock files. Blocking calls; callers must run these
 * off the main thread (Dispatchers.IO), same as the app's own HTTPClient.
 *
 * See STATUS.md Part -20 for why this exists: the whole point of the real
 * Atlas client is that sing-box IS the client, with no separate Tailscale
 * app and no manual "paste a URL" step -- sign-in itself is what registers
 * the device and produces a working config.
 */
object AtlasApi {
    private const val BASE_URL = "https://dashboard.layer9i.com"

    class AtlasApiException(message: String) : Exception(message)

    private fun request(
        path: String,
        token: String? = null,
        body: JSONObject? = null,
    ): JSONObject {
        val connection = URL("$BASE_URL$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Content-Type", "application/json")
            if (token != null) {
                connection.setRequestProperty("X-Presence-Token", token)
            }
            connection.doOutput = true
            OutputStreamWriter(connection.outputStream).use { it.write((body ?: JSONObject()).toString()) }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
            val json = if (text.isNotBlank()) JSONObject(text) else JSONObject()
            if (code !in 200..299) {
                throw AtlasApiException(json.optString("detail", "Request failed ($code)"))
            }
            return json
        } finally {
            connection.disconnect()
        }
    }

    /** Returns the presence token on success. */
    fun login(email: String, password: String): String {
        val body = JSONObject().put("email", email).put("password", password)
        return request("/api/presence/login", body = body).getString("token")
    }

    /** Returns the raw sing-box VPN config as a JSON string, ready to write to a profile file. */
    fun enrollNative(token: String, deviceLabel: String): String =
        request("/api/presence/enroll/native", token = token, body = JSONObject().put("device_label", deviceLabel))
            .getJSONObject("singbox_config")
            .toString()
}
