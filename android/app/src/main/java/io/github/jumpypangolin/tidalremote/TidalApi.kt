package io.github.jumpypangolin.tidalremote

import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Minimal client for the official TIDAL API (client credentials, catalog data only). Blocking: call off the main thread. */
class TidalApi(private val clientId: String, private val clientSecret: String, private val country: String) {

    private var token: String? = null
    private var expiresAt = 0L

    /** ID of the track radio mix for a Tidal track. */
    fun trackRadioMixId(trackId: String): String {
        val url = "https://openapi.tidal.com/v2/tracks/${enc(trackId)}/relationships/radio?countryCode=${enc(country)}"
        val json = request(url, "GET", null, "Bearer ${token()}", "application/vnd.api+json")
        val data = json.optJSONArray("data")
        if (data == null || data.length() == 0) throw TidalException("No radio for this track")
        return data.getJSONObject(0).getString("id")
    }

    private fun token(): String {
        token?.let { if (System.currentTimeMillis() < expiresAt) return it }
        if (clientId.isBlank() || clientSecret.isBlank()) throw TidalException("Tidal API login missing in settings")
        val basic = Base64.encodeToString("$clientId:$clientSecret".toByteArray(), Base64.NO_WRAP)
        val json = request("https://auth.tidal.com/v1/oauth2/token", "POST",
            "grant_type=client_credentials", "Basic $basic", "application/json")
        token = json.getString("access_token")
        expiresAt = System.currentTimeMillis() + (json.optLong("expires_in", 3600) - 60) * 1000
        return token!!
    }

    private fun request(url: String, method: String, body: String?, auth: String, accept: String): JSONObject {
        val con = URL(url).openConnection() as HttpURLConnection
        try {
            con.requestMethod = method
            con.connectTimeout = 5000
            con.readTimeout = 5000
            con.setRequestProperty("Authorization", auth)
            con.setRequestProperty("Accept", accept)
            if (body != null) {
                con.doOutput = true
                con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                con.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = con.responseCode
            if (code == 401) token = null
            if (code !in 200..299) throw TidalException("Tidal API error $code")
            return JSONObject(con.inputStream.bufferedReader().use { it.readText() })
        } finally {
            con.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
