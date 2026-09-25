package ru.cultureguide.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ru.cultureguide.model.Place
import java.util.Locale

/**
 * Аудиогид: читает историческую справку объекта голосом системного синтезатора речи.
 * Работает без сети, если в системе установлен русский голос.
 */
class AudioGuide(context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var ready = false
    private var pending: Place? = null

    /** Объект, справка о котором звучит сейчас; null — тишина. */
    var speakingPlaceId by mutableStateOf<Long?>(null)
        private set

    /** false, если синтезатор речи недоступен или нет русского голоса. */
    var available by mutableStateOf(true)
        private set

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        main.post { onInit(status) }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit

            override fun onDone(utteranceId: String) {
                main.post { if (speakingPlaceId?.toString() == utteranceId) speakingPlaceId = null }
            }

            @Deprecated("Deprecated in Android API")
            override fun onError(utteranceId: String) {
                main.post { speakingPlaceId = null }
            }
        })
    }

    private fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            available = false
            return
        }
        val result = tts.setLanguage(Locale.forLanguageTag("ru-RU"))
        available = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        ready = available
        pending?.takeIf { ready }?.let(::speak)
        pending = null
    }

    fun speak(place: Place) {
        if (!ready) {
            pending = place
            return
        }
        val text = buildString {
            append(place.name).append(". ")
            append(place.description.replace("\n", " "))
        }
        speakingPlaceId = place.id
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, place.id.toString())
    }

    fun toggle(place: Place) {
        if (speakingPlaceId == place.id) stop() else speak(place)
    }

    fun stop() {
        pending = null
        tts.stop()
        speakingPlaceId = null
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
