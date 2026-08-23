package com.vibeforge.botarena

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object OpenRouter {

    private const val BASE = "https://openrouter.ai/api/v1"

    class ApiException(message: String) : Exception(message)

    fun chat(apiKey: String, model: String, temperature: Double, system: String, user: String): String {
        if (apiKey.isBlank()) throw ApiException("No OpenRouter key saved. Add one in Settings.")

        val body = JSONObject()
        body.put("model", model)
        body.put("temperature", temperature)
        body.put("max_tokens", 700)
        val msgs = JSONArray()
        msgs.put(JSONObject().put("role", "system").put("content", system))
        msgs.put(JSONObject().put("role", "user").put("content", user))
        body.put("messages", msgs)

        val conn = URL("$BASE/chat/completions").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 20000
            conn.readTimeout = 90000
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("HTTP-Referer", "https://github.com/vibeforge/botarena")
            conn.setRequestProperty("X-Title", "Bot Arena")

            val w = OutputStreamWriter(conn.outputStream, "UTF-8")
            w.write(body.toString())
            w.flush()
            w.close()

            val code = conn.responseCode
            val text = readAll(conn, code >= 400)
            if (code >= 400) throw ApiException("OpenRouter returned $code: " + shorten(text))

            val o = JSONObject(text)
            if (o.has("error")) {
                val err = o.optJSONObject("error")
                throw ApiException(err?.optString("message") ?: "OpenRouter reported an error")
            }
            val choices = o.optJSONArray("choices")
                ?: throw ApiException("Reply had no choices: " + shorten(text))
            if (choices.length() == 0) throw ApiException("The model returned no choices.")
            val msg = choices.getJSONObject(0).optJSONObject("message")
                ?: throw ApiException("Reply had no message: " + shorten(text))
            val content = msg.optString("content", "")
            if (content.isBlank()) throw ApiException("The model returned an empty reply.")
            return content
        } finally {
            conn.disconnect()
        }
    }

    fun listModels(apiKey: String): List<String> {
        val conn = URL("$BASE/models").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 20000
            conn.readTimeout = 40000
            if (apiKey.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer $apiKey")

            val code = conn.responseCode
            val text = readAll(conn, code >= 400)
            if (code >= 400) throw ApiException("Model list failed with $code: " + shorten(text))

            val arr = JSONObject(text).optJSONArray("data")
                ?: throw ApiException("Model list came back in an unexpected shape.")
            val out = ArrayList<String>()
            for (i in 0 until arr.length()) {
                val id = arr.optJSONObject(i)?.optString("id") ?: continue
                if (id.isNotBlank()) out.add(id)
            }
            out.sort()
            if (out.isEmpty()) throw ApiException("Model list came back empty.")
            return out
        } finally {
            conn.disconnect()
        }
    }

    private fun readAll(conn: HttpURLConnection, isError: Boolean): String {
        val stream = if (isError) (conn.errorStream ?: conn.inputStream) else conn.inputStream
        val r = BufferedReader(InputStreamReader(stream, "UTF-8"))
        val sb = StringBuilder()
        var line = r.readLine()
        while (line != null) {
            sb.append(line).append("\n")
            line = r.readLine()
        }
        r.close()
        return sb.toString()
    }

    private fun shorten(s: String): String {
        val t = s.replace("\n", " ").trim()
        return if (t.length > 220) t.substring(0, 220) + "…" else t
    }
}
