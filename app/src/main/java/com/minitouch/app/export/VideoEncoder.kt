package com.minitouch.app.export

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import java.nio.ByteBuffer

/**
 * Encoder H.264 que recebe frames via Surface (renderizados diretamente pelo
 * OpenGL do pipeline — sem cópia CPU) e escreve um .mp4 com MediaMuxer.
 *
 * Uso:
 *   val encoder = VideoEncoder(outputPath, width, height, bitrate)
 *   encoder.start()
 *   val inputSurface = encoder.inputSurface  // usado para criar a EGLSurface do pipeline
 *   // a cada frame: desenhe no inputSurface, então chame encoder.drainEncoder(false)
 *   encoder.drainEncoder(endOfStream = true)
 *   encoder.stop()
 */
class VideoEncoder(
    private val outputPath: String,
    private val width: Int,
    private val height: Int,
    private val bitRate: Int = 8_000_000,
    private val frameRate: Int = 30,
) {
    private lateinit var codec: MediaCodec
    lateinit var inputSurface: Surface
        private set
    private lateinit var muxer: MediaMuxer
    private var trackIndex = -1
    private var muxerStarted = false
    private val bufferInfo = MediaCodec.BufferInfo()

    fun start() {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }

        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = codec.createInputSurface()
        codec.start()

        muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    /** Chame após cada frame desenhado no inputSurface (com swapBuffers já feito). */
    fun drainEncoder(endOfStream: Boolean) {
        if (endOfStream) codec.signalEndOfInputStream()

        while (true) {
            val outputBufferId = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            when {
                outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) return
                }
                outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    check(!muxerStarted) { "Formato mudou mais de uma vez" }
                    trackIndex = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }
                outputBufferId >= 0 -> {
                    val encodedData: ByteBuffer = codec.getOutputBuffer(outputBufferId)
                        ?: error("outputBuffer $outputBufferId nulo")

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size != 0) {
                        check(muxerStarted) { "Muxer não iniciado antes de escrever dados" }
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                    }

                    codec.releaseOutputBuffer(outputBufferId, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    fun stop() {
        codec.stop()
        codec.release()
        if (muxerStarted) {
            muxer.stop()
        }
        muxer.release()
    }

    companion object {
        private const val TIMEOUT_US = 10_000L
    }
}
