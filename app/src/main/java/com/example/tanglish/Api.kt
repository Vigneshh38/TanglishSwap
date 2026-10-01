package com.example.tanglish

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Api {
    private const val SYSTEM = """You convert Tamil and Telugu written in English letters.
You get a JSON array of strings. For each string:
- If it is Tamil written in English letters, write the same meaning in Telugu using English letters.
- If it is Telugu written in English letters, write the same meaning in Tamil using English letters.
- If it is normal English, a name, a number, or anything else, use an empty string.
Reply with ONLY a JSON array of objects like [{"i":0,"out":"..."}]. Keep the same order. No extra text."""

    /** Returns map of original text -> converted text ("" means leave as is). */
    fun convert(apiKey: String, texts: List<String>): Map<String, String> {
        val body = JSONObject().apply {
            put("model", "claude-haiku-4-5-20251001")
            put("max_tokens", 2000)
            put("system", SYSTEM)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", JSONArray(texts).toString())
            }))
        }

        val conn = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 10000
        conn.readTimeout = 30000
        conn.doOutput = true
        conn.setRequestProperty("content-type", "application/json")
        conn.setRequestProperty("x-api-key", apiKey)
        conn.setRequestProperty("anthropic-version", "2023-06-01")
        conn.outputStream.use { it.write(body.toString().toByteArray()) }

        if (conn.responseCode !in 200..299) {
            throw RuntimeException("API error ${conn.responseCode}")
        }
        val resp = conn.inputStream.bufferedReader().readText()
        var text = JSONObject(resp).getJSONArray("content").getJSONObject(0).getString("text").trim()
        text = text.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

        val arr = JSONArray(text)
        val result = HashMap<String, String>()
        for (n in 0 until arr.length()) {
            val o = arr.getJSONObject(n)
            val idx = o.getInt("i")
            if (idx in texts.indices) result[texts[idx]] = o.optString("out", "")
        }
        return result
    }
}
