package com.arabicuieditor.data

import com.arabicuieditor.data.ai.AiClient
import com.arabicuieditor.data.ai.AiProvider
import com.arabicuieditor.data.ai.AiResult
import com.arabicuieditor.data.ai.AiSettings
import com.arabicuieditor.data.github.Http
import com.arabicuieditor.data.github.HttpResponse
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiClientTest {
    private class Fake(val code: Int, val body: String) : Http {
        var url = ""; var headers: Map<String, String> = emptyMap(); var sent = ""
        override fun request(method: String, url: String, headers: Map<String, String>, body: String?, contentType: String): HttpResponse {
            this.url = url; this.headers = headers; sent = body.orEmpty()
            return HttpResponse(code, this.body)
        }
    }

    private val openai = """{"choices":[{"message":{"content":"{\"ok\":1}"}}]}"""
    private val anth = """{"content":[{"type":"text","text":"مرحبا"}]}"""

    @Test fun everyProviderHasHttpsBaseAndModelExceptCustom() {
        for (p in AiProvider.all.filter { it.id != AiProvider.CUSTOM }) {
            assertTrue(p.id, p.baseUrl.startsWith("https://"))
            assertTrue(p.id, p.defaultModel.isNotEmpty())
            assertTrue(p.id, p.keyPage.startsWith("https://"))
        }
        assertEquals(AiProvider.all.size, AiProvider.all.map { it.id }.toSet().size)
    }

    @Test fun openAiStyleRequestAndReply() {
        val s = AiSettings("groq", " k123 ")
        val h = Fake(200, openai)
        val r = AiClient.complete(h, s, "سؤال")
        assertEquals(AiResult.Ok("{\"ok\":1}"), r)
        assertEquals("https://api.groq.com/openai/v1/chat/completions", h.url)
        assertEquals("Bearer k123", h.headers["Authorization"])
        val body = JSONObject(h.sent)
        assertEquals("llama-3.3-70b-versatile", body.getString("model"))
        assertEquals("سؤال", body.getJSONArray("messages").getJSONObject(0).getString("content"))
    }

    @Test fun anthropicUsesItsOwnHeadersAndShape() {
        val h = Fake(200, anth)
        val r = AiClient.complete(h, AiSettings("anthropic", "sk"), "x")
        assertEquals(AiResult.Ok("مرحبا"), r)
        assertEquals("https://api.anthropic.com/v1/messages", h.url)
        assertEquals("sk", h.headers["x-api-key"])
        assertTrue(h.headers.containsKey("anthropic-version"))
        assertTrue(JSONObject(h.sent).has("max_tokens"))
    }

    @Test fun customProviderNeedsHttpsBaseAndModel() {
        assertFalse(AiSettings(AiProvider.CUSTOM, "k", "m", "http://x.com").isUsable)
        assertFalse(AiSettings(AiProvider.CUSTOM, "k", "", "https://x.com/v1").isUsable)
        val s = AiSettings(AiProvider.CUSTOM, "k", "m", "https://x.com/v1/")
        assertTrue(s.isUsable)
        val h = Fake(200, openai)
        AiClient.complete(h, s, "x")
        assertEquals("https://x.com/v1/chat/completions", h.url)
    }

    @Test fun errorsAreClassified() {
        val s = AiSettings("openai", "k")
        fun reason(code: Int) = (AiClient.complete(Fake(code, "{}"), s, "x") as AiResult.Fail).reason
        assertEquals(AiResult.Reason.BAD_KEY, reason(401))
        assertEquals(AiResult.Reason.RATE_LIMIT, reason(429))
        assertEquals(AiResult.Reason.OTHER, reason(500))
        assertEquals(AiResult.Reason.BAD_RESPONSE, (AiClient.complete(Fake(200, "not json"), s, "x") as AiResult.Fail).reason)
        assertEquals(AiResult.Reason.NO_SETTINGS, (AiClient.complete(Fake(200, openai), AiSettings("openai", ""), "x") as AiResult.Fail).reason)
    }

    @Test fun networkExceptionIsReported() {
        val h = object : Http {
            override fun request(method: String, url: String, headers: Map<String, String>, body: String?, contentType: String): HttpResponse =
                throw java.io.IOException("down")
        }
        assertEquals(AiResult.Reason.NETWORK, (AiClient.complete(h, AiSettings("groq", "k"), "x") as AiResult.Fail).reason)
    }
}
