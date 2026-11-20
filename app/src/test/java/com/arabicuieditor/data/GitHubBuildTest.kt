package com.arabicuieditor.data

import com.arabicuieditor.data.github.BuildState
import com.arabicuieditor.data.github.GitHubApi
import com.arabicuieditor.data.github.GitHubBuilder
import com.arabicuieditor.data.github.GitHubException
import com.arabicuieditor.data.github.Http
import com.arabicuieditor.data.github.HttpResponse
import com.arabicuieditor.data.github.TokenPoll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GitHubBuildTest {
    private class Call(val method: String, val url: String, val body: String?)

    /** مزيّف: يطابق بداية «METHOD url» ويعيد ردودًا بالترتيب. */
    private class Fake(private val routes: List<Pair<String, List<HttpResponse>>>) : Http {
        val calls = mutableListOf<Call>()
        private val used = HashMap<String, Int>()
        override fun request(method: String, url: String, headers: Map<String, String>, body: String?, contentType: String): HttpResponse {
            calls += Call(method, url, body)
            val key = "$method $url"
            val route = routes.firstOrNull { key.startsWith(it.first) } ?: return HttpResponse(404, "{}")
            val i = used.getOrDefault(route.first, 0)
            used[route.first] = i + 1
            return route.second[minOf(i, route.second.size - 1)]
        }
    }

    private fun ok(body: String, code: Int = 200) = HttpResponse(code, body)

    @Test fun deviceFlowStartParsed() {
        val f = Fake(listOf("POST https://github.com/login/device/code" to listOf(
            ok("""{"device_code":"dc","user_code":"ABCD-1234","verification_uri":"https://github.com/login/device","interval":5,"expires_in":900}"""))))
        val d = GitHubApi(f).startDeviceFlow("cid")
        assertEquals("ABCD-1234", d.userCode)
        assertEquals(5, d.intervalSec)
        assertTrue(f.calls[0].body!!.contains("client_id=cid"))
        assertTrue(f.calls[0].body!!.contains("scope=repo+workflow"))
    }

    @Test fun pollStates() {
        fun poll(json: String) = GitHubApi(Fake(listOf("POST https://github.com/login/oauth" to listOf(ok(json))))).pollToken("c", "d")
        assertEquals(TokenPoll.Pending, poll("""{"error":"authorization_pending"}"""))
        assertEquals(TokenPoll.SlowDown, poll("""{"error":"slow_down"}"""))
        assertEquals("tok", (poll("""{"access_token":"tok"}""") as TokenPoll.Token).token)
        try { poll("""{"error":"expired_token"}"""); fail() } catch (_: GitHubException) {}
        try { poll("""{"error":"access_denied"}"""); fail() } catch (_: GitHubException) {}
    }

    @Test fun repoNameIsSafe() {
        assertEquals("my-app-abc123", GitHubApi.repoName("My App!", "abc123-xyz"))
        assertEquals("app-x", GitHubApi.repoName("تطبيقي", "---"))
        assertTrue(GitHubApi.repoName("a".repeat(100), "id1").length <= 40)
    }

    private fun fullRoutes(runs: List<HttpResponse>) = listOf(
        "GET https://api.github.com/user" to listOf(ok("""{"login":"me"}""")),
        "POST https://api.github.com/user/repos" to listOf(ok("""{"default_branch":"main"}""", 201)),
        "GET https://api.github.com/repos/me/r/git/ref" to listOf(ok("""{"object":{"sha":"p0"}}""")),
        "POST https://api.github.com/repos/me/r/git/blobs" to listOf(ok("""{"sha":"b1"}""", 201)),
        "POST https://api.github.com/repos/me/r/git/trees" to listOf(ok("""{"sha":"t1"}""", 201)),
        "POST https://api.github.com/repos/me/r/git/commits" to listOf(ok("""{"sha":"c1"}""", 201)),
        "PATCH https://api.github.com/repos/me/r/git/refs/heads/main" to listOf(ok("""{}""")),
        "GET https://api.github.com/repos/me/r/actions/runs" to runs
    )

    @Test fun pushSequenceAndRunDone() = runBlocking {
        val f = Fake(fullRoutes(listOf(
            ok("""{"workflow_runs":[]}"""),
            ok("""{"workflow_runs":[{"id":7,"status":"in_progress","html_url":"https://x/run/7"}]}"""),
            ok("""{"workflow_runs":[{"id":7,"status":"completed","conclusion":"success","html_url":"https://x/run/7"}]}"""))))
        val states = mutableListOf<BuildState>()
        val r = GitHubBuilder(GitHubApi(f), sleep = {}, maxPolls = 10)
            .build("t", "r", linkedMapOf("a.txt" to "hi".toByteArray(), "b/c.txt" to ByteArray(2)), states::add)
        assertEquals(BuildState.Done("https://x/run/7", true), r)
        val seq = f.calls.map { it.method + " " + it.url.substringAfter("me/r/").substringBefore('?') }
        assertEquals(2, seq.count { it == "POST git/blobs" })
        assertTrue(seq.indexOf("POST git/trees") > seq.lastIndexOf("POST git/blobs"))
        assertTrue(seq.indexOf("PATCH git/refs/heads/main") > seq.indexOf("POST git/commits"))
        assertTrue(f.calls.first { it.url.endsWith("git/commits") }.body!!.contains("p0"))
        assertTrue(f.calls.first { it.url.contains("actions/runs") }.url.contains("head_sha=c1"))
        assertTrue(states.any { it is BuildState.Pushing })
    }

    @Test fun failedRunReported() = runBlocking {
        val f = Fake(fullRoutes(listOf(ok("""{"workflow_runs":[{"id":1,"status":"completed","conclusion":"failure","html_url":"u"}]}"""))))
        val r = GitHubBuilder(GitHubApi(f), sleep = {}).build("t", "r", mapOf("a" to ByteArray(1)), {})
        assertEquals(BuildState.Done("u", false), r)
    }

    @Test fun unauthorizedFlagged() = runBlocking {
        val f = Fake(listOf("GET https://api.github.com/user" to listOf(HttpResponse(401, """{"message":"Bad credentials"}"""))))
        val r = GitHubBuilder(GitHubApi(f), sleep = {}).build("t", "r", emptyMap(), {})
        assertTrue(r is BuildState.Failed && r.unauthorized)
    }

    @Test fun existingRepoReused() {
        val f = Fake(listOf(
            "POST https://api.github.com/user/repos" to listOf(ok("""{"message":"exists"}""", 422)),
            "GET https://api.github.com/repos/me/r" to listOf(ok("""{"default_branch":"dev"}"""))))
        assertEquals("dev", GitHubApi(f).ensureRepo("t", "me", "r").branch)
    }

    @Test fun authorizeHandlesSlowDownThenToken() = runBlocking {
        val f = Fake(listOf(
            "POST https://github.com/login/device/code" to listOf(ok("""{"device_code":"d","user_code":"U","verification_uri":"v","interval":1,"expires_in":100}""")),
            "POST https://github.com/login/oauth" to listOf(ok("""{"error":"authorization_pending"}"""), ok("""{"error":"slow_down"}"""), ok("""{"access_token":"T"}"""))))
        val waits = mutableListOf<Long>()
        val token = GitHubBuilder(GitHubApi(f), sleep = { waits += it }).authorize("c", {})
        assertEquals("T", token)
        assertEquals(listOf(1000L, 1000L, 6000L), waits)
    }

    @Test fun noRunYetIsNull() {
        val f = Fake(listOf("GET https://api.github.com/repos/me/r/actions/runs" to listOf(ok("""{"workflow_runs":[]}"""))))
        assertNull(GitHubApi(f).runForCommit("t", com.arabicuieditor.data.github.RepoInfo("me", "r", "main"), "c1"))
        assertFalse(f.calls.isEmpty())
    }

    @Test fun resumeUsesExistingCodeWithoutNewRequest() = runBlocking {
        val f = Fake(listOf("POST https://github.com/login/oauth" to listOf(ok("""{"access_token":"T2"}"""))))
        val existing = com.arabicuieditor.data.github.DeviceCode("d0", "U0", "v", 1, 100)
        val states = mutableListOf<BuildState>()
        val token = GitHubBuilder(GitHubApi(f), sleep = {}).authorize("c", states::add, resume = existing)
        assertEquals("T2", token)
        assertTrue(f.calls.none { it.url.contains("device/code") })
        assertEquals(BuildState.NeedAuth("U0", "v"), states.first())
    }
}
