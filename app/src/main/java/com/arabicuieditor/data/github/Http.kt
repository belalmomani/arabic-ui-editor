package com.arabicuieditor.data.github

data class HttpResponse(val code: Int, val body: String)

/** واجهة HTTP صغيرة تُستبدل بمزيّف في الاختبارات. */
interface Http {
    fun request(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        contentType: String = JSON
    ): HttpResponse

    companion object {
        const val JSON = "application/json; charset=utf-8"
        const val FORM = "application/x-www-form-urlencoded"
    }
}

/** تنفيذ حقيقي بـ HttpURLConnection (يدعم أندرويد PATCH). */
class UrlConnectionHttp : Http {
    override fun request(method: String, url: String, headers: Map<String, String>, body: String?, contentType: String): HttpResponse {
        val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        try {
            c.connectTimeout = 20_000
            c.readTimeout = 30_000
            c.requestMethod = method
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (method != "GET" && method != "HEAD") {
                c.doOutput = true
                c.setRequestProperty("Content-Type", contentType)
                c.outputStream.use { it.write((body ?: "").toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code >= 400) c.errorStream else c.inputStream
            return HttpResponse(code, stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty())
        } finally {
            c.disconnect()
        }
    }
}
