package com.arabicuieditor.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import com.arabicuieditor.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * الإعلانات: بانر في شاشة المشاريع، وبيني واحد بعد نجاح تصدير/بناء (فاصل طبيعي) — لا عند الفتح ولا الإغلاق
 * ولا أثناء التصميم. لا يُطلب أي إعلان قبل نافذة الموافقة (UMP) حيث تلزم.
 * النسخة التجريبية (debug) تستخدم معرّفات اختبار Google، والإصدار (release) المعرّفات الحقيقية.
 */
object AdsManager {
    private val readyFlow = MutableStateFlow(false)
    val ready: StateFlow<Boolean> get() = readyFlow

    private var interstitial: InterstitialAd? = null
    private var loading = false
    private var started = false
    private val throttle = AdThrottle()

    fun start(activity: Activity) {
        if (started) return
        started = true
        val info = UserMessagingPlatform.getConsentInformation(activity)
        val appContext = activity.applicationContext
        info.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    if (info.canRequestAds()) initAds(appContext)
                }
            },
            { if (info.canRequestAds()) initAds(appContext) }
        )
    }

    private fun initAds(context: Context) {
        if (readyFlow.value) return
        MobileAds.initialize(context) {}
        readyFlow.value = true
        preload(context)
    }

    private fun preload(context: Context) {
        if (loading || interstitial != null) return
        loading = true
        InterstitialAd.load(
            context, BuildConfig.AD_INTERSTITIAL_ID, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { interstitial = ad; loading = false }
                override fun onAdFailedToLoad(error: LoadAdError) { interstitial = null; loading = false }
            }
        )
    }

    /** يُستدعى بعد نجاح عملية كاملة فقط (تصدير ZIP أو بناء GitHub)، على الخيط الرئيسي. */
    fun showInterstitial(context: Context) {
        if (!readyFlow.value) return
        val activity = findActivity(context) ?: return
        val appContext = activity.applicationContext
        val ad = interstitial
        if (ad == null) { preload(appContext); return }
        val now = SystemClock.elapsedRealtime()
        if (!throttle.canShow(now)) return
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { interstitial = null; preload(appContext) }
            override fun onAdFailedToShowFullScreenContent(error: AdError) { interstitial = null; preload(appContext) }
        }
        throttle.markShown(now)
        ad.show(activity)
    }

    private fun findActivity(context: Context): Activity? {
        var c: Context? = context
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }
}
