package com.arabicuieditor.ads

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import com.arabicuieditor.BuildConfig
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/** بانر صغير؛ لا يُنشأ إلا بعد جاهزية الإعلانات (بعد الموافقة). */
@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    val ready by AdsManager.ready.collectAsState()
    if (ready) {
        AndroidView(
            modifier = modifier.fillMaxWidth().navigationBarsPadding().testTag("banner_ad"),
            factory = { ctx ->
                AdView(ctx).apply {
                    setAdSize(AdSize.BANNER)
                    adUnitId = BuildConfig.AD_BANNER_ID
                    loadAd(AdRequest.Builder().build())
                }
            }
        )
    }
}
