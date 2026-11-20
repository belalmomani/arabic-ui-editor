package com.arabicuieditor.data.github

import kotlinx.coroutines.delay

sealed interface BuildState {
    /** بانتظار أن يُدخل المستخدم [userCode] في [uri]. */
    data class NeedAuth(val userCode: String, val uri: String) : BuildState
    data class Pushing(val done: Int, val total: Int) : BuildState
    data class Building(val runUrl: String?, val status: String) : BuildState
    data class Done(val runUrl: String, val success: Boolean) : BuildState
    data class Failed(val reason: String, val unauthorized: Boolean = false) : BuildState
}

/** يدير تسلسل: تسجيل دخول (إن لزم) ← إنشاء مستودع ← رفع ← انتظار تشغيل Actions. يُستدعى من خيط خلفي. */
class GitHubBuilder(
    private val api: GitHubApi,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val pollMs: Long = 10_000,
    private val maxPolls: Int = 180
) {
    /** يعيد الرمز بعد موافقة المستخدم، أو يرمي [GitHubException]. */
    suspend fun authorize(
        clientId: String, onState: (BuildState) -> Unit,
        resume: DeviceCode? = null, onCode: (DeviceCode) -> Unit = {}
    ): String {
        val code = resume ?: api.startDeviceFlow(clientId).also(onCode)
        onState(BuildState.NeedAuth(code.userCode, code.verificationUri))
        var interval = code.intervalSec.coerceAtLeast(1)
        var waited = 0
        while (waited <= code.expiresInSec) {
            sleep(interval * 1000L)
            waited += interval
            when (val p = api.pollToken(clientId, code.deviceCode)) {
                is TokenPoll.Token -> return p.token
                TokenPoll.Pending -> Unit
                TokenPoll.SlowDown -> interval += 5
            }
        }
        throw GitHubException("انتهت مهلة الموافقة")
    }

    suspend fun build(token: String, repoName: String, files: Map<String, ByteArray>, onState: (BuildState) -> Unit): BuildState {
        return try {
            onState(BuildState.Pushing(0, files.size))
            val login = api.userLogin(token)
            val repo = api.ensureRepo(token, login, repoName)
            val sha = api.pushFiles(token, repo, files, "Build from Arabic UI Editor") { d, t -> onState(BuildState.Pushing(d, t)) }
            onState(BuildState.Building(null, "queued"))
            repeat(maxPolls) {
                val run = api.runForCommit(token, repo, sha)
                if (run != null) {
                    if (run.status == "completed") return BuildState.Done(run.url, run.conclusion == "success")
                    onState(BuildState.Building(run.url, run.status))
                }
                sleep(pollMs)
            }
            BuildState.Failed("استغرق البناء وقتًا أطول من المتوقع؛ تابعه من صفحة Actions في المستودع")
        } catch (e: GitHubException) {
            BuildState.Failed(e.message.orEmpty(), unauthorized = e.code == 401)
        } catch (e: java.io.IOException) {
            BuildState.Failed("تعذّر الاتصال بالشبكة")
        }
    }
}
