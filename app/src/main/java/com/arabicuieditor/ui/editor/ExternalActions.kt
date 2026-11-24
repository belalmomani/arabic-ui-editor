package com.arabicuieditor.ui.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.arabicuieditor.data.model.ClickAction
import com.arabicuieditor.data.model.ExternalKinds

/** ينفّذ إجراءً خارجيًا على الجهاز (المعاينة). يعيد false إن كانت القيمة غير صالحة أو لا تطبيق يستقبلها. */
object ExternalActions {
    fun open(context: Context, a: ClickAction.External): Boolean {
        val uri = ExternalKinds.uri(a.kind, a.value) ?: return false
        val intent = when (a.kind) {
            ExternalKinds.CALL -> Intent(Intent.ACTION_DIAL, Uri.parse(uri))
            ExternalKinds.SHARE -> Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, uri), null)
            else -> Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { context.startActivity(intent); true } catch (_: Exception) { false }
    }
}
