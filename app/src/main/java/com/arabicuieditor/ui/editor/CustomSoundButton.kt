package com.arabicuieditor.ui.editor

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.arabicuieditor.R
import com.arabicuieditor.data.storage.SoundImporter
import com.arabicuieditor.ui.editor.canvas.LocalImageDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** رقاقة «من جهازي»: تختار ملف صوت (wav/mp3/ogg/m4a حتى 1.5 ميغابايت) وتخزّنه مع المشروع وتسلّم اسمه. */
@Composable
fun CustomSoundButton(onPicked: (String) -> Unit, tag: String) {
    val dir = LocalImageDir.current ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val name = withContext(Dispatchers.IO) { SoundImporter.import(context, uri, dir) }
            if (name != null) onPicked(name)
            else Toast.makeText(context, context.getString(R.string.snd_import_failed), Toast.LENGTH_LONG).show()
        }
    }
    FilterChip(
        selected = false,
        onClick = { picker.launch("audio/*") },
        label = { Text(stringResource(R.string.snd_import)) },
        modifier = Modifier.testTag(tag)
    )
}
