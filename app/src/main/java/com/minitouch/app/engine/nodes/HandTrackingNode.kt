package com.minitouch.app.engine.nodes

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import com.minitouch.app.engine.node.FrameData
import com.minitouch.app.engine.node.Node
import com.minitouch.app.hands.HandLandmarkerHelper
import com.minitouch.app.hands.HandResult
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Nó "passivo": não modifica a imagem, só observa a textura de entrada e roda a IA de mãos
 * em paralelo. Como MediaPipe (CPU/GPU delegate) é mais lento que 1 frame de câmera,
 * rodamos no máximo 1 detecção por vez (dropar frames em vez de enfileirar) e throttled
 * por targetIntervalMs para não saturar a CPU.
 *
 * Este nó NÃO tem textureId próprio: ele repassa o input adiante, só enriquecendo com `hands`.
 */
class HandTrackingNode(
    override val id: String = "hand_tracking",
    context: Context,
    private val targetIntervalMs: Long = 66L, // ~15 detecções/s é suficiente para tracking suave
) : Node {

    private val helper = HandLandmarkerHelper(context, onResult = { latestResult.set(it) })
    private val executor = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private val latestResult = AtomicReference<HandResult?>(null)
    private var lastSubmitMs = 0L

    // Buffer reutilizável para glReadPixels -> Bitmap (evita alocar a cada frame)
    private var pixelBuffer: ByteBuffer? = null
    private var bufferWidth = 0
    private var bufferHeight = 0

    override fun process(inputs: List<FrameData>): FrameData {
        val input = inputs.firstOrNull() ?: return FrameData()
        val now = System.currentTimeMillis()

        if (!busy.get() && now - lastSubmitMs >= targetIntervalMs && input.textureId != 0) {
            lastSubmitMs = now
            submitFrameForDetection(input)
        }

        return input.copy(hands = latestResult.get())
    }

    private fun submitFrameForDetection(frame: FrameData) {
        // glReadPixels precisa rodar na GL thread, então lemos os pixels aqui...
        val bitmap = readTextureAsBitmap(frame) ?: return
        busy.set(true)
        // ...e mandamos o Bitmap (já em memória, sem depender de GL) pra thread da IA.
        executor.execute {
            try {
                helper.detectAsync(bitmap, frame.timestampNs / 1_000_000)
            } finally {
                busy.set(false)
            }
        }
    }

    /**
     * Lê a textura atual via um FBO temporário + glReadPixels.
     * Nota: assume que `frame.textureId` já é uma textura GL_TEXTURE_2D (ou seja, este nó
     * deve vir DEPOIS de um nó que converte OES->2D, como o ShaderEffectNode). Se vier OES
     * direto da câmera, envolva-a num framebuffer OES-attach antes (glFramebufferTexture2D
     * aceita textura OES normalmente).
     */
    private fun readTextureAsBitmap(frame: FrameData): Bitmap? {
        if (frame.width <= 0 || frame.height <= 0) return null
        val w = frame.width
        val h = frame.height

        if (bufferWidth != w || bufferHeight != h) {
            pixelBuffer = ByteBuffer.allocateDirect(w * h * 4)
            bufferWidth = w
            bufferHeight = h
        }
        val buffer = pixelBuffer!!
        buffer.rewind()

        val fbo = IntArray(1)
        GLES20.glGenFramebuffers(1, fbo, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0])
        val target = if (frame.isOes) android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES else GLES20.GL_TEXTURE_2D
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, target, frame.textureId, 0)

        if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glDeleteFramebuffers(1, fbo, 0)
            return null
        }

        GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glDeleteFramebuffers(1, fbo, 0)

        buffer.rewind()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }

    override fun onDestroy() {
        executor.shutdown()
        helper.close()
    }
}
