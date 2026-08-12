package com.luminara.app.data

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.channels.Channel
import java.io.ByteArrayOutputStream

/**
 * Microphone capture for Live Lecture.
 *
 * Records 16 kHz mono PCM — exactly what Whisper wants, so the backend needs no
 * ffmpeg and no resampling — and emits complete WAV chunks on a channel. The
 * read loop never waits for a chunk to upload: chunks go into the channel and a
 * separate coroutine posts them, so audio is not dropped while the network is
 * busy.
 */
class LiveRecorder(
    private val chunkSeconds: Int = 9,
    private val sampleRate: Int = 16_000,
) {
    /** Completed WAV chunks, in order. */
    val chunks = Channel<ByteArray>(capacity = Channel.BUFFERED)

    @Volatile
    var paused: Boolean = false

    @Volatile
    private var recording: Boolean = false

    private var recorder: AudioRecord? = null

    /** Loudest sample in the most recent read, 0..1 — drives the level meter. */
    @Volatile
    var level: Float = 0f
        private set

    val bytesPerChunk: Int get() = sampleRate * chunkSeconds * 2   // 16-bit mono

    @SuppressLint("MissingPermission")   // caller checks RECORD_AUDIO first
    fun start(): Boolean {
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) return false

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer * 4, bytesPerChunk / 4),
        )
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            return false
        }

        recorder = audioRecord
        recording = true
        audioRecord.startRecording()
        return true
    }

    /** Blocking read loop. Run it on Dispatchers.IO. */
    suspend fun readLoop() {
        val audioRecord = recorder ?: return
        val buffer = ByteArray(4096)
        val pending = ByteArrayOutputStream(bytesPerChunk)

        while (recording) {
            val read = audioRecord.read(buffer, 0, buffer.size)
            if (read <= 0) continue

            if (paused) {
                level = 0f
                continue          // keep the mic open, simply discard while paused
            }

            level = peak(buffer, read)
            pending.write(buffer, 0, read)

            if (pending.size() >= bytesPerChunk) {
                chunks.send(wav(pending.toByteArray()))
                pending.reset()
            }
        }

        // Send the tail so the last few seconds of the lecture are not lost.
        if (pending.size() > sampleRate) {          // at least ~0.5s of audio
            chunks.send(wav(pending.toByteArray()))
        }
        chunks.close()
    }

    fun stop() {
        recording = false
        runCatching {
            recorder?.stop()
            recorder?.release()
        }
        recorder = null
        level = 0f
    }

    private fun peak(buffer: ByteArray, length: Int): Float {
        var max = 0
        var i = 0
        while (i + 1 < length) {
            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort()
            val magnitude = kotlin.math.abs(sample.toInt())
            if (magnitude > max) max = magnitude
            i += 2
        }
        return (max / 32768f).coerceIn(0f, 1f)
    }

    /** Wrap raw PCM in a WAV header so the backend can decode it with the stdlib. */
    private fun wav(pcm: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(pcm.size + 44)
        val byteRate = sampleRate * 2
        fun int(value: Int) {
            out.write(value and 0xFF)
            out.write((value shr 8) and 0xFF)
            out.write((value shr 16) and 0xFF)
            out.write((value shr 24) and 0xFF)
        }
        fun short(value: Int) {
            out.write(value and 0xFF)
            out.write((value shr 8) and 0xFF)
        }

        out.write("RIFF".toByteArray())
        int(36 + pcm.size)
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        int(16)          // PCM header size
        short(1)         // PCM format
        short(1)         // mono
        int(sampleRate)
        int(byteRate)
        short(2)         // block align
        short(16)        // bits per sample
        out.write("data".toByteArray())
        int(pcm.size)
        out.write(pcm)
        return out.toByteArray()
    }
}
