package ru.cultureguide.kids.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.IOException

/** Проигрывает заранее озвученные фрагменты из `assets/kids/audio` один за другим. */
class ClipPlayer(context: Context) {
    private val assets = context.applicationContext.assets
    private val queue = ArrayDeque<String>()
    private var player: MediaPlayer? = null

    /** Фрагмент, который звучит сейчас; null — тишина. */
    var playing by mutableStateOf<String?>(null)
        private set

    fun play(clips: List<String>) {
        stop()
        queue.addAll(clips)
        playNext()
    }

    fun play(vararg clips: String) = play(clips.toList())

    fun stop() {
        queue.clear()
        release()
        playing = null
    }

    private fun playNext() {
        release()
        val name = queue.removeFirstOrNull()
        playing = name
        if (name == null) return
        val mp = MediaPlayer()
        try {
            assets.openFd("$AUDIO_DIR/$name.ogg").use { mp.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mp.setOnCompletionListener { playNext() }
            mp.setOnErrorListener { _, _, _ -> playNext(); true }
            mp.prepare()
            player = mp
            mp.start()
        } catch (_: IOException) {
            mp.release()
            playNext()
        } catch (_: IllegalStateException) {
            mp.release()
            playNext()
        }
    }

    private fun release() {
        player?.release()
        player = null
    }

    private companion object {
        const val AUDIO_DIR = "kids/audio"
    }
}
