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
    fun convert(apiKey: String, model: String, texts: List<String>): Map<String, String> {
        val body = JSONObject().apply {
            put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM))))
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", JSONArray(texts).toString())))
            }))
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0)
            })
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 10000
        conn.readTimeout = 30000
        conn.doOutput = true
        conn.setRequestProperty("content-type", "application/json")
        conn.setRequestProperty("x-goog-api-key", apiKey)
        conn.outputStream.use { it.write(body.toString().toByteArray()) }

        if (conn.responseCode !in 200..299) {
            val msg = try { conn.errorStream?.bufferedReader()?.readText()?.take(200) } catch (e: Exception) { "" }
            throw RuntimeException("Error ${conn.responseCode}: $msg")
        }
        val resp = conn.inputStream.bufferedReader().readText()
        var text = JSONObject(resp).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text").trim()
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
