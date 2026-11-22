package com.arabicuieditor.data.ai

import com.arabicuieditor.data.github.Http
import org.json.JSONArray
import org.json.JSONObject

/** نتيجة طلب: نص الرد أو سبب الفشل. */
sealed class AiResult {
    data class Ok(val text: String) : AiResult()
    enum class Reason { NO_SETTINGS, BAD_KEY, RATE_LIMIT, NETWORK, BAD_RESPONSE, OTHER }
    data class Fail(val reason: Reason, val detail: String = "") : AiResult()
}

/** عميل بسيط: يرسل طلبًا واحدًا (الرسالة نصها [prompt]) ويعيد نص الرد. المفتاح يُرسل إلى المزوّد المختار فقط. */
object AiClient {
    const val MAX_TOKENS = 6000

    fun buildRequest(s: AiSettings, prompt: String): Triple<String, Map<String, String>, String> {
        val p = s.provider
        return if (p.anthropic) {
            val body = JSONObject().put("model", s.effectiveModel).put("max_tokens", MAX_TOKENS)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            Triple(
                s.effectiveBaseUrl + "/v1/messages",
                mapOf("x-api-key" to s.apiKey.trim(), "anthropic-version" to "2023-06-01"),
                body.toString()
            )
        } else {
            val body = JSONObject().put("model", s.effectiveModel).put("max_tokens", MAX_TOKENS)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            Triple(
                s.effectiveBaseUrl + "/chat/completions",
                mapOf("Authorization" to "Bearer " + s.apiKey.trim()),
                body.toString()
            )
        }
    }

    /** يستخرج نص الرد من JSON المزوّد، أو null إن لم يوجد. */
    fun parseReply(s: AiSettings, body: String): String? = try {
        val o = JSONObject(body)
        if (s.provider.anthropic) {
            val arr = o.optJSONArray("content")
            buildString { if (arr != null) for (i in 0 until arr.length()) append(arr.optJSONObject(i)?.optString("text").orEmpty()) }
        } else {
            o.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
        }.takeIf { it.isNotBlank() }
    } catch (_: Exception) { null }

    fun complete(http: Http, s: AiSettings, prompt: String): AiResult {
        if (!s.isUsable) return AiResult.Fail(AiResult.Reason.NO_SETTINGS)
        val (url, headers, body) = buildRequest(s, prompt)
        val r = try { http.request("POST", url, headers, body) } catch (e: Exception) {
            return AiResult.Fail(AiResult.Reason.NETWORK, e.javaClass.simpleName + ": " + e.message.orEmpty().take(120))
        }
        return when {
            r.code in 200..299 -> parseReply(s, r.body)?.let { AiResult.Ok(it) } ?: AiResult.Fail(AiResult.Reason.BAD_RESPONSE, r.body.take(160))
            r.code == 401 || r.code == 403 -> AiResult.Fail(AiResult.Reason.BAD_KEY, r.body.take(160))
            r.code == 429 -> AiResult.Fail(AiResult.Reason.RATE_LIMIT, r.body.take(160))
            else -> AiResult.Fail(AiResult.Reason.OTHER, "HTTP ${r.code}: " + r.body.take(160))
        }
    }
}
