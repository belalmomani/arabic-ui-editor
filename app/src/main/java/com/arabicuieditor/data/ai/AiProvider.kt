package com.arabicuieditor.data.ai

/** مزوّد ذكاء اصطناعي: معظمهم يدعم واجهة OpenAI المتوافقة (/chat/completions)، وAnthropic له واجهته. */
data class AiProvider(
    val id: String,
    val label: String,
    val baseUrl: String,
    val defaultModel: String,
    val free: Boolean,
    /** صفحة إنشاء المفتاح (للإرشاد فقط). */
    val keyPage: String,
    val anthropic: Boolean = false
) {
    companion object {
        const val CUSTOM = "custom"

        val all: List<AiProvider> = listOf(
            AiProvider("gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash", true, "https://aistudio.google.com/app/apikey"),
            AiProvider("groq", "Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", true, "https://console.groq.com/keys"),
            AiProvider("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", "meta-llama/llama-3.3-70b-instruct:free", true, "https://openrouter.ai/keys"),
            AiProvider("mistral", "Mistral AI", "https://api.mistral.ai/v1", "mistral-small-latest", true, "https://console.mistral.ai/api-keys/"),
            AiProvider("cerebras", "Cerebras", "https://api.cerebras.ai/v1", "llama-3.3-70b", true, "https://cloud.cerebras.ai/"),
            AiProvider("sambanova", "SambaNova", "https://api.sambanova.ai/v1", "Meta-Llama-3.3-70B-Instruct", true, "https://cloud.sambanova.ai/"),
            AiProvider("nvidia", "NVIDIA NIM", "https://integrate.api.nvidia.com/v1", "meta/llama-3.3-70b-instruct", true, "https://build.nvidia.com/"),
            AiProvider("huggingface", "Hugging Face", "https://router.huggingface.co/v1", "meta-llama/Llama-3.3-70B-Instruct", true, "https://huggingface.co/settings/tokens"),
            AiProvider("cohere", "Cohere", "https://api.cohere.ai/compatibility/v1", "command-a-03-2025", true, "https://dashboard.cohere.com/api-keys"),
            AiProvider("openai", "OpenAI", "https://api.openai.com/v1", "gpt-4o-mini", false, "https://platform.openai.com/api-keys"),
            AiProvider("anthropic", "Claude (Anthropic)", "https://api.anthropic.com", "claude-haiku-4-5", false, "https://console.anthropic.com/settings/keys", anthropic = true),
            AiProvider("deepseek", "DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat", false, "https://platform.deepseek.com/api_keys"),
            AiProvider("xai", "xAI Grok", "https://api.x.ai/v1", "grok-3-mini", false, "https://console.x.ai/"),
            AiProvider("together", "Together AI", "https://api.together.xyz/v1", "meta-llama/Llama-3.3-70B-Instruct-Turbo", false, "https://api.together.ai/"),
            AiProvider("fireworks", "Fireworks AI", "https://api.fireworks.ai/inference/v1", "accounts/fireworks/models/llama-v3p3-70b-instruct", false, "https://fireworks.ai/"),
            AiProvider("deepinfra", "DeepInfra", "https://api.deepinfra.com/v1/openai", "meta-llama/Llama-3.3-70B-Instruct", false, "https://deepinfra.com/"),
            AiProvider("perplexity", "Perplexity", "https://api.perplexity.ai", "sonar", false, "https://www.perplexity.ai/settings/api"),
            AiProvider("novita", "Novita AI", "https://api.novita.ai/v3/openai", "meta-llama/llama-3.3-70b-instruct", false, "https://novita.ai/"),
            AiProvider(CUSTOM, "مزوّد آخر (متوافق مع OpenAI)", "", "", false, "")
        )

        fun byId(id: String): AiProvider = all.firstOrNull { it.id == id } ?: all.first()
    }
}

/** إعداد المستخدم الاختياري: المزوّد والمفتاح والنموذج (وعنوان مخصص للمزوّد الآخر). */
data class AiSettings(
    val providerId: String,
    val apiKey: String,
    val model: String = "",
    val customBaseUrl: String = ""
) {
    val provider: AiProvider get() = AiProvider.byId(providerId)
    val effectiveModel: String get() = model.trim().ifEmpty { provider.defaultModel }
    val effectiveBaseUrl: String get() = (if (providerId == AiProvider.CUSTOM) customBaseUrl else provider.baseUrl).trim().trimEnd('/')
    val isUsable: Boolean get() = apiKey.isNotBlank() && effectiveBaseUrl.startsWith("https://") && effectiveModel.isNotEmpty()
}
