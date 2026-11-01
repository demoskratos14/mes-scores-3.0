package com.aventure.messcores

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import java.io.IOException
import java.nio.ByteOrder
import kotlin.math.max

/** Le son d'une chanson en mono, ramené à une fréquence d'échantillonnage basse (assez pour les graves comme pour la mélodie). */
internal class DecodedAudio(val samples: FloatArray, val sampleRate: Int)

/** La chanson est trop longue pour être analysée (voir [RhythmAudioDecoder.MAX_SECONDS]). */
internal class RhythmTooLongException : IOException("chanson trop longue")

/**
 * Lit un fichier audio du téléphone (MP3, M4A, OGG, WAV, FLAC…) avec les décodeurs d'Android, sans le jouer, et en tire
 * les échantillons sonores. Le décodage va bien plus vite que la durée de la chanson.
 */
internal object RhythmAudioDecoder {

    /** On travaille autour de 11 kHz : les fréquences au-dessus de 5 kHz ne servent pas à repérer le rythme. */
    private const val TARGET_RATE = 11_025
    const val MAX_SECONDS = 15 * 60
    private const val TIMEOUT_US = 10_000L

    /**
     * Décode [uri]. [onProgress] reçoit un nombre entre 0 et 1 ; [checkCancel] est appelée régulièrement et peut lever
     * une exception pour interrompre le travail. Lève une [IOException] si le fichier n'est pas un son lisible.
     */
    fun decode(context: Context, uri: Uri, onProgress: (Float) -> Unit, checkCancel: () -> Unit): DecodedAudio {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(i)
                if (candidate.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    track = i
                    format = candidate
                    break
                }
            }
            if (track < 0 || format == null) throw IOException("aucune piste audio")
            extractor.selectTrack(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: throw IOException("format inconnu")
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            if (durationUs > MAX_SECONDS * 1_000_000L) throw RhythmTooLongException()

            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = max(1, format.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            var factor = max(1, sampleRate / TARGET_RATE)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            var out = FloatArray(max(4_096, (durationUs / 1_000_000.0 * TARGET_RATE).toInt() + 4_096))
            var count = 0
            var acc = 0f
            var accFrames = 0

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                checkCancel()
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex) ?: throw IOException("tampon indisponible")
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val changed = codec.outputFormat
                    if (changed.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = changed.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    if (changed.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = max(1, changed.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
                    encoding = if (changed.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        changed.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    } else {
                        AudioFormat.ENCODING_PCM_16BIT
                    }
                    factor = max(1, sampleRate / TARGET_RATE)
                } else if (outIndex >= 0) {
                    if (info.size > 0) {
                        val buffer = codec.getOutputBuffer(outIndex) ?: throw IOException("tampon indisponible")
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        buffer.order(ByteOrder.nativeOrder())
                        val frames: Int
                        if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
                            val floats = buffer.asFloatBuffer()
                            frames = floats.remaining() / channels
                            if (count + frames / factor + 2 > out.size) out = out.copyOf(max(out.size * 2, count + frames / factor + 2))
                            repeat(frames) {
                                var sum = 0f
                                repeat(channels) { sum += floats.get() }
                                acc += sum / channels
                                if (++accFrames == factor) {
                                    out[count++] = acc / factor
                                    acc = 0f
                                    accFrames = 0
                                }
                            }
                        } else {
                            val shorts = buffer.asShortBuffer()
                            frames = shorts.remaining() / channels
                            if (count + frames / factor + 2 > out.size) out = out.copyOf(max(out.size * 2, count + frames / factor + 2))
                            repeat(frames) {
                                var sum = 0
                                repeat(channels) { sum += shorts.get().toInt() }
                                acc += sum / (channels * 32_768f)
                                if (++accFrames == factor) {
                                    out[count++] = acc / factor
                                    acc = 0f
                                    accFrames = 0
                                }
                            }
                        }
                        if (durationUs > 0) onProgress((info.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f))
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
                if (count > MAX_SECONDS * TARGET_RATE * 2) throw RhythmTooLongException()
            }
            if (count == 0) throw IOException("aucun son décodé")
            onProgress(1f)
            return DecodedAudio(out.copyOf(count), max(1, sampleRate / factor))
        } finally {
            try {
                codec?.stop()
            } catch (e: IllegalStateException) {
                // Décodeur déjà arrêté ou en erreur : rien d'autre à faire que le libérer.
            }
            codec?.release()
            extractor.release()
        }
    }

    /** Le nom du fichier choisi, sans son extension, pour l'afficher. */
    fun displayName(context: Context, uri: Uri): String? {
        val name = try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (e: SecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        } ?: uri.lastPathSegment
        return name?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
    }
}
