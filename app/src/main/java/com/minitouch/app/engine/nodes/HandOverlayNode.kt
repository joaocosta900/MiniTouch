package com.minitouch.app.engine.nodes

import android.opengl.GLES20
import com.minitouch.app.engine.gl.FullFrameQuad
import com.minitouch.app.engine.gl.GlTextures
import com.minitouch.app.engine.node.FrameData
import com.minitouch.app.engine.node.Node
import com.minitouch.app.hands.Hand
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

// Pares de índices de landmarks que formam os "ossos" da mão (topologia do MediaPipe Hands)
private val HAND_CONNECTIONS = listOf(
    0 to 1, 1 to 2, 2 to 3, 3 to 4,          // polegar
    0 to 5, 5 to 6, 6 to 7, 7 to 8,          // indicador
    5 to 9, 9 to 10, 10 to 11, 11 to 12,     // médio
    9 to 13, 13 to 14, 14 to 15, 15 to 16,   // anelar
    13 to 17, 17 to 18, 18 to 19, 19 to 20,  // mínimo
    0 to 17,                                  // base da palma
)

private const val LINE_VERTEX_SHADER = """
    attribute vec2 aPosition;
    void main() {
        gl_Position = vec4(aPosition, 0.0, 1.0);
        gl_PointSize = 8.0;
    }
"""

private const val LINE_FRAGMENT_SHADER = """
    precision mediump float;
    uniform vec4 uColor;
    void main() {
        gl_FragColor = uColor;
    }
"""

/**
 * Redesenha a textura de entrada num FBO e sobrepõe linhas/pontos representando
 * o esqueleto de cada mão detectada (input.hands, vindo do HandTrackingNode).
 */
class HandOverlayNode(
    override val id: String = "hand_overlay",
    private val width: Int,
    private val height: Int,
) : Node {

    private val quad = FullFrameQuad()
    private lateinit var lineProgram: com.minitouch.app.engine.gl.ShaderProgram
    private var outputTexture = 0
    private var fbo = 0

    override fun onCreate() {
        lineProgram = com.minitouch.app.engine.gl.ShaderProgram(LINE_VERTEX_SHADER, LINE_FRAGMENT_SHADER)
        outputTexture = GlTextures.createTexture2D(width, height)
        fbo = GlTextures.createFramebuffer(outputTexture)
    }

    override fun process(inputs: List<FrameData>): FrameData {
        // Espera 2 inputs: [0] = textura vinda do ShaderEffectNode, [1] = metadados do HandTrackingNode
        val videoFrame = inputs.getOrNull(0) ?: return FrameData()
        val handsFrame = inputs.getOrNull(1)
        val hands = handsFrame?.hands ?: videoFrame.hands

        if (videoFrame.textureId == 0) return videoFrame

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glViewport(0, 0, width, height)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // 1. Desenha o vídeo de base
        quad.drawWithShader(quad.defaultProgram2D(), videoFrame.textureId, isOes = videoFrame.isOes)

        // 2. Sobrepõe o esqueleto de cada mão
        hands?.hands?.forEach { hand -> drawHandSkeleton(hand) }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

        return videoFrame.copy(textureId = outputTexture, isOes = false)
    }

    private fun drawHandSkeleton(hand: Hand) {
        if (hand.landmarks.size < 21) return

        lineProgram.use()
        val posLoc = lineProgram.attribLocation("aPosition")
        val color = if (hand.isRight) floatArrayOf(0.2f, 1f, 0.4f, 1f) else floatArrayOf(1f, 0.6f, 0.2f, 1f)
        GLES20.glUniform4fv(lineProgram.uniformLocation("uColor"), 1, color, 0)

        // Converte landmarks normalizados (0..1, origem no canto superior esquerdo)
        // para coordenadas de clipe (-1..1, origem no centro, Y invertido).
        val lineVerts = FloatArray(HAND_CONNECTIONS.size * 4)
        HAND_CONNECTIONS.forEachIndexed { i, (a, b) ->
            val pa = hand.landmarks[a]
            val pb = hand.landmarks[b]
            lineVerts[i * 4] = pa.x * 2f - 1f
            lineVerts[i * 4 + 1] = 1f - pa.y * 2f
            lineVerts[i * 4 + 2] = pb.x * 2f - 1f
            lineVerts[i * 4 + 3] = 1f - pb.y * 2f
        }
        val lineBuffer = toBuffer(lineVerts)
        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 0, lineBuffer)
        GLES20.glLineWidth(4f)
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, HAND_CONNECTIONS.size * 2)

        val pointVerts = FloatArray(hand.landmarks.size * 2)
        hand.landmarks.forEachIndexed { i, p ->
            pointVerts[i * 2] = p.x * 2f - 1f
            pointVerts[i * 2 + 1] = 1f - p.y * 2f
        }
        val pointBuffer = toBuffer(pointVerts)
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 0, pointBuffer)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, hand.landmarks.size)

        GLES20.glDisableVertexAttribArray(posLoc)
    }

    private fun toBuffer(arr: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(arr.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(arr); position(0)
        }

    override fun onDestroy() {
        lineProgram.release()
        quad.release()
        GlTextures.deleteFramebuffer(fbo)
        GlTextures.deleteTexture(outputTexture)
    }
}
