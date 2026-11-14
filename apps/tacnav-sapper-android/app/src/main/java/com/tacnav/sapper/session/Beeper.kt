package com.tacnav.sapper.session

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.tacnav.sapper.game.SignalPort
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** Short 1 kHz signals, generated instead of shipped as sound files. */
object Tone {
    const val SAMPLE_RATE = 44_100
    const val FREQUENCY_HZ = 1_000.0
    const val BEEP_MILLIS = 150
    const val GAP_MILLIS = 120
    private const val FADE_MILLIS = 5
    private const val AMPLITUDE = 0.8

    fun samples(millis: Int): Int = SAMPLE_RATE * millis / 1_000

    /** [count] beeps separated by silence, as 16-bit mono PCM. */
    fun pattern(count: Int): ShortArray {
        require(count in 1..3) { "Signals have one to three beeps." }
        val beep = beep()
        val gap = ShortArray(samples(GAP_MILLIS))
        val out = ArrayList<Short>((beep.size + gap.size) * count)
        repeat(count) { index ->
            out.addAll(beep.asList())
            if (index < count - 1) out.addAll(gap.asList())
        }
        return out.toShortArray()
    }

    private fun beep(): ShortArray {
        val length = samples(BEEP_MILLIS)
        val fade = samples(FADE_MILLIS)
        return ShortArray(length) { index ->
            val envelope = min(1.0, min(index, length - 1 - index).toDouble() / fade)
            val value = sin(2 * PI * FREQUENCY_HZ * index / SAMPLE_RATE) * AMPLITUDE * envelope
            (value * Short.MAX_VALUE).toInt().toShort()
        }
    }
}

/** Plays signals on the alarm channel, so media volume does not mute them. */
class Beeper : SignalPort {
    private var track: AudioTrack? = null

    @Synchronized
    override fun play(count: Int) {
        val pcm = Tone.pattern(count.coerceIn(1, 3))
        track?.release()
        val next = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(Tone.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        next.write(pcm, 0, pcm.size)
        next.play()
        track = next
    }

    @Synchronized
    fun release() {
        track?.release()
        track = null
    }
}
