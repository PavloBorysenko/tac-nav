package com.tacnav.mortar.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.tacnav.mortar.R

object ShotFeedback {
    @Volatile
    private var pool: SoundPool? = null

    @Volatile
    private var shotId: Int = 0

    fun play(context: Context) {
        val sounds = pool ?: SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
            .also { created ->
                pool = created
                shotId = created.load(context, R.raw.shot, 1)
            }
        val id = shotId
        if (id != 0) {
            sounds.play(id, 1f, 1f, 1, 0, 1f)
        }
    }
}
