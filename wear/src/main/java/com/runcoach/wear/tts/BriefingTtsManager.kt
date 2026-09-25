package com.runcoach.wear.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.runcoach.wear.data.model.CoachStyle
import java.util.Locale

class BriefingTtsManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false
    private var pendingSpeech: Pair<String, CoachStyle>? = null

    var onStart: (() -> Unit)? = null
    var onDone: (() -> Unit)? = null
    var onError: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.KOREAN
                isReady = true
                // TTS 준비 완료 시 대기 중인 음성 발화
                pendingSpeech?.let { (text, style) ->
                    pendingSpeech = null
                    speak(text, style)
                }
            }
        }
    }

    fun speak(text: String, coachStyle: CoachStyle) {
        if (!isReady) {
            // 준비 완료 후 자동 발화 (가장 최신 메시지만 유지)
            pendingSpeech = Pair(text, coachStyle)
            return
        }

        tts?.apply {
            stop()
            setSpeechRate(coachStyle.ttsRate)
            setPitch(coachStyle.ttsPitch)

            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    onStart?.invoke()
                }
                override fun onDone(utteranceId: String?) {
                    onDone?.invoke()
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    onError?.invoke()
                }
            })

            // 긴 텍스트는 청크로 나눠서 처리
            val chunks = splitIntoChunks(text)
            chunks.forEachIndexed { index, chunk ->
                val utteranceId = if (index == chunks.lastIndex) "DONE" else "CHUNK_$index"
                speak(chunk, TextToSpeech.QUEUE_ADD, null, utteranceId)
            }
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }

    // 300자 이상 텍스트는 문장 단위로 분리 (TTS 안정성 향상)
    private fun splitIntoChunks(text: String, maxLength: Int = 200): List<String> {
        if (text.length <= maxLength) return listOf(text)

        val chunks = mutableListOf<String>()
        val sentences = text.split(Regex("(?<=[.!?。]) +|(?<=요\\.) |(?<=다\\.) "))
        var current = StringBuilder()

        for (sentence in sentences) {
            if (current.length + sentence.length > maxLength) {
                if (current.isNotEmpty()) chunks.add(current.toString().trim())
                current = StringBuilder(sentence)
            } else {
                current.append(" ").append(sentence)
            }
        }
        if (current.isNotEmpty()) chunks.add(current.toString().trim())
        return chunks.ifEmpty { listOf(text) }
    }
}
