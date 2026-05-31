package com.example.game

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sin

object AudioSynthesizer {
    private val scope = CoroutineScope(Dispatchers.Default)
    private const val SAMPLE_RATE = 22050

    fun playLaserSound() {
        scope.launch {
            try {
                val duration = 0.12f
                val numSamples = (SAMPLE_RATE * duration).toInt()
                val buffer = ShortArray(numSamples)
                for (i in 0 until numSamples) {
                    val progress = i.toFloat() / numSamples
                    // Frequency swept downwards for retro zap
                    val freq = 1300f - (1100f * progress)
                    val angle = 2.0 * Math.PI * freq * (i.toFloat() / SAMPLE_RATE)
                    // Synthesize soft sine wave
                    buffer[i] = (sin(angle) * Short.MAX_VALUE * 0.3f).toInt().toShort()
                }
                playBuffer(buffer)
            } catch (e: Exception) {
                // Audio not supported by emulator/system environment, fail silently
            }
        }
    }

    fun playExplosionSound() {
        scope.launch {
            try {
                val duration = 0.35f
                val numSamples = (SAMPLE_RATE * duration).toInt()
                val buffer = ShortArray(numSamples)
                for (i in 0 until numSamples) {
                    val progress = i.toFloat() / numSamples
                    val amp = 1.0f - progress // linear volume decay
                    // White noise mixed with low rumble frequency
                    val noise = (Math.random() * 2.0 - 1.0) * Short.MAX_VALUE * 0.32f * amp
                    val rumble = sin(2.0 * Math.PI * 55.0 * (i.toFloat() / SAMPLE_RATE)) * Short.MAX_VALUE * 0.25f * amp
                    buffer[i] = ((noise + rumble) * 0.7f).toInt().toShort()
                }
                playBuffer(buffer)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun playPowerupSound() {
        scope.launch {
            try {
                val duration = 0.25f
                val numSamples = (SAMPLE_RATE * duration).toInt()
                val buffer = ShortArray(numSamples)
                for (i in 0 until numSamples) {
                    val progress = i.toFloat() / numSamples
                    // Short ascending chord
                    val freq = when {
                        progress < 0.33f -> 523.25f // C5
                        progress < 0.66f -> 659.25f // E5
                        else -> 783.99f // G5
                    }
                    val angle = 2.0 * Math.PI * freq * (i.toFloat() / SAMPLE_RATE)
                    buffer[i] = (sin(angle) * Short.MAX_VALUE * 0.25f).toInt().toShort()
                }
                playBuffer(buffer)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun playUpgradeSound() {
        scope.launch {
            try {
                val duration = 0.45f
                val numSamples = (SAMPLE_RATE * duration).toInt()
                val buffer = ShortArray(numSamples)
                for (i in 0 until numSamples) {
                    val progress = i.toFloat() / numSamples
                    // Fast bubbling upward frequency sweep
                    val freq = 400f + (1400f * progress * progress)
                    val angle = 2.0 * Math.PI * freq * (i.toFloat() / SAMPLE_RATE)
                    buffer[i] = (sin(angle) * Short.MAX_VALUE * 0.3f).toInt().toShort()
                }
                playBuffer(buffer)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun playBuffer(buffer: ShortArray) {
        val audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            buffer.size * 2,
            AudioTrack.MODE_STATIC
        )
        audioTrack.write(buffer, 0, buffer.size)
        audioTrack.play()
        // static buffer needs brief delay before clean release
        scope.launch(Dispatchers.IO) {
            try {
                kotlinx.coroutines.delay((buffer.size.toFloat() / SAMPLE_RATE * 1000).toLong() + 150)
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
