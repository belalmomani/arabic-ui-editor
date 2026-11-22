package com.arabicuieditor.ui.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arabicuieditor.R
import com.arabicuieditor.ui.theme.AppButton
import com.arabicuieditor.ui.theme.AppOutlinedButton

/** الشاشة الوسيطة بعد الإنشاء التلقائي: المشروع محفوظ بالفعل؛ اختر المحرر أو أضف معلوماتك لاحقًا. */
@Composable
fun SetupScreen(projectId: String, onBack: () -> Unit, onOpenEditor: () -> Unit, onOpenFill: (() -> Unit)? = null) {
    Surface(Modifier.fillMaxSize().testTag("setup_screen")) {
        Column(
            Modifier.statusBarsPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.setup_saved), style = MaterialTheme.typography.bodyMedium)
            AppButton(onClick = onOpenEditor, modifier = Modifier.fillMaxWidth().testTag("setup_open_editor")) {
                Text(stringResource(R.string.setup_open_editor))
            }
            if (onOpenFill != null) {
                AppOutlinedButton(onClick = onOpenFill, modifier = Modifier.fillMaxWidth().testTag("setup_fill")) {
                    Text(stringResource(R.string.setup_fill))
                }
                Text(stringResource(R.string.setup_fill_hint), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onBack, modifier = Modifier.testTag("setup_back")) {
                Text(stringResource(R.string.setup_back))
            }
        }
    }
}
