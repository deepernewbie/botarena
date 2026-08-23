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

    fun chat(
        apiKey: String,
        model: String,
        temperature: Double,
        thinking: String,
        system: String,
        user: String
    ): String {
        if (apiKey.isBlank()) throw ApiException("No OpenRouter key saved. Add one in Settings.")

        val body = JSONObject()
        body.put("model", model)
        body.put("temperature", temperature)
        body.put("max_tokens", 2000)
        val msgs = JSONArray()
        msgs.put(JSONObject().put("role", "system").put("content", system))
        msgs.put(JSONObject().put("role", "user").put("content", user))
        body.put("messages", msgs)

        // A robot needs a move, not an essay. Reasoning tokens come out of the same
        // budget as the answer, so a thinking model can spend the lot and say nothing.
        when (thinking) {
            THINK_OFF -> body.put("reasoning", JSONObject().put("enabled", false).put("exclude", true))
            THINK_BRIEF -> body.put("reasoning", JSONObject().put("effort", "low").put("exclude", true))
        }

        try {
            return send(apiKey, body)
        } catch (e: ApiException) {
            // Some endpoints make reasoning mandatory and reject the switch outright.
            val msg = e.message ?: ""
            if (body.has("reasoning") && msg.contains("reasoning", true)) {
                body.remove("reasoning")
                return send(apiKey, body)
            }
            throw e
        }
    }

    private fun send(apiKey: String, body: JSONObject): String {
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
            val choice = choices.getJSONObject(0)
            val msg = choice.optJSONObject("message")
                ?: throw ApiException("Reply had no message: " + shorten(text))

            val content = contentOf(msg)
            if (content.isNotBlank()) return content

            // Nothing usable. Work out why, because "empty reply" helps nobody.
            val finish = choice.optString("finish_reason", "")
            val thought = reasoningOf(msg)
            if (thought.isNotBlank()) {
                throw ApiException(
                    "The model wrote " + thought.length + " characters of thinking and never got to an " +
                        "answer. Set Thinking to Off in the harness editor, or pick a model that " +
                        "doesn't reason. Its last words: \"" + shorten(thought.takeLast(160)) + "\""
                )
            }
            throw ApiException(
                when (finish) {
                    "length" -> "The model hit the token ceiling before writing anything."
                    "content_filter" -> "The provider's content filter blocked the reply."
                    else -> "The model sent a message with no text in it."
                } + " (finish_reason: " + (if (finish.isBlank()) "none" else finish) + ")"
            )
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Text can be a plain string or an array of typed parts. Note that optString on
     * a JSON null returns the string "null", so isNull has to be checked first.
     */
    private fun contentOf(msg: JSONObject): String {
        if (msg.isNull("content")) return ""
        val c = msg.opt("content")
        if (c is String) return if (c == "null") "" else c
        if (c is JSONArray) {
            val sb = StringBuilder()
            for (i in 0 until c.length()) {
                val part = c.optJSONObject(i) ?: continue
                val t = part.optString("text", "")
                if (t.isNotBlank()) sb.append(t).append("\n")
            }
            return sb.toString()
        }
        return ""
    }

    private fun reasoningOf(msg: JSONObject): String {
        if (!msg.isNull("reasoning")) {
            val r = msg.optString("reasoning", "")
            if (r.isNotBlank() && r != "null") return r
        }
        if (!msg.isNull("refusal")) {
            val r = msg.optString("refusal", "")
            if (r.isNotBlank() && r != "null") return "Refused: " + r
        }
        return ""
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
