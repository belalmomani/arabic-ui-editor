package com.arabicuieditor.ui.common

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.arabicuieditor.data.model.Sounds
import com.arabicuieditor.ui.editor.canvas.LocalImageDir
import java.io.File

/**
 * يشغّل الأصوات: المضمّنة (assets/sounds) بـ SoundPool، والمستورَدة من الجهاز (في مجلد موارد المشروع [customDir])
 * بـ MediaPlayer. أي معرّف غير صالح أو «بدون» يُتجاهل. يجب استدعاء [release] عند الانتهاء.
 */
class SoundPlayer(private val context: Context, private val customDir: File? = null) {
    // يُنشأ عند أول تشغيل فقط (الشاشات التي لا تشغّل صوتًا لا تكلّف شيئًا).
    private val poolDelegate = lazy {
        SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
            .also { p ->
                p.setOnLoadCompleteListener { _, sampleId, status ->
                    if (status == 0) {
                        ready += sampleId
                        if (waiting.remove(sampleId)) p.play(sampleId, 1f, 1f, 1, 0, 1f)
                    }
                }
            }
    }
    private val pool: SoundPool by poolDelegate
    private val loaded = HashMap<String, Int>()
    private val ready = HashSet<Int>()
    private val waiting = HashSet<Int>()
    private val players = ArrayList<MediaPlayer>()

    fun play(sound: String) {
        if (!Sounds.isValid(sound)) return
        if (Sounds.isCustom(sound)) { playCustom(sound); return }
        val id = loaded.getOrPut(sound) {
            try {
                context.assets.openFd(Sounds.assetPath(sound)).use { pool.load(it, 1) }
            } catch (_: Exception) {
                0
            }
        }
        if (id == 0) return
        if (id in ready) pool.play(id, 1f, 1f, 1, 0, 1f) else waiting += id
    }

    private fun playCustom(sound: String) {
        val f = customDir?.let { File(it, sound) }?.takeIf { it.isFile } ?: return
        try {
            val mp = MediaPlayer()
            players += mp
            mp.setDataSource(f.absolutePath)
            mp.setOnCompletionListener { players -= it; it.release() }
            mp.setOnErrorListener { p, _, _ -> players -= p; p.release(); true }
            mp.setOnPreparedListener { it.start() }
            mp.prepareAsync()
        } catch (_: Exception) {
        }
    }

    fun release() {
        if (poolDelegate.isInitialized()) pool.release()
        players.toList().forEach { try { it.release() } catch (_: Exception) {} }
        players.clear()
    }
}

@Composable
fun rememberSoundPlayer(): SoundPlayer {
    val context = LocalContext.current.applicationContext
    val dir = LocalImageDir.current
    val player = remember(dir) { SoundPlayer(context, dir) }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}
