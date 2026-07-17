package com.minitouch.app.engine.gl

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

private const val VERTEX_OES = """
    attribute vec4 aPosition;
    attribute vec2 aTexCoord;
    varying vec2 vTexCoord;
    void main() {
        gl_Position = aPosition;
        vTexCoord = aTexCoord;
    }
"""

private const val FRAGMENT_OES_PASSTHROUGH = """
    #extension GL_OES_EGL_image_external : require
    precision mediump float;
    varying vec2 vTexCoord;
    uniform samplerExternalOES uTexture;
    void main() {
        gl_FragColor = texture2D(uTexture, vTexCoord);
    }
"""

private const val FRAGMENT_2D_PASSTHROUGH = """
    precision mediump float;
    varying vec2 vTexCoord;
    uniform sampler2D uTexture;
    void main() {
        gl_FragColor = texture2D(uTexture, vTexCoord);
    }
"""

/**
 * Desenha um quad cobrindo a tela inteira, amostrando uma textura (OES ou 2D)
 * através de um ShaderProgram customizável. É a base usada por ShaderEffectNode,
 * HandOverlayNode e OutputNode — todos "desenham uma textura na tela/FBO com um shader".
 */
class FullFrameQuad {
    private val vertexBuffer: FloatBuffer
    private val texCoordBuffer: FloatBuffer

    private var oesProgram: ShaderProgram? = null
    private var tex2dProgram: ShaderProgram? = null

    init {
        // x, y para 2 triângulos formando um quad (-1..1)
        val verts = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        val texCoords = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)
        vertexBuffer = toFloatBuffer(verts)
        texCoordBuffer = toFloatBuffer(texCoords)
    }

    private fun toFloatBuffer(arr: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(arr.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(arr); position(0)
        }

    /** Desenha a textura OES (câmera) usando o shader passthrough padrão. */
    fun drawOes(textureId: Int) {
        val program = oesProgram ?: ShaderProgram(VERTEX_OES, FRAGMENT_OES_PASSTHROUGH).also { oesProgram = it }
        drawInternal(program, textureId, isOes = true)
    }

    /**
     * Desenha uma textura com um fragment shader customizado.
     * isOes deve corresponder ao tipo de sampler declarado no shader
     * (samplerExternalOES vs sampler2D) e ao tipo real da textura.
     */
    fun drawWithShader(program: ShaderProgram, textureId: Int, isOes: Boolean = false) {
        drawInternal(program, textureId, isOes)
    }

    fun defaultProgram2D(): ShaderProgram =
        tex2dProgram ?: ShaderProgram(VERTEX_OES, FRAGMENT_2D_PASSTHROUGH).also { tex2dProgram = it }

    private fun drawInternal(program: ShaderProgram, textureId: Int, isOes: Boolean) {
        program.use()

        val posLoc = program.attribLocation("aPosition")
        val texLoc = program.attribLocation("aTexCoord")

        GLES20.glEnableVertexAttribArray(posLoc)
        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        GLES20.glEnableVertexAttribArray(texLoc)
        texCoordBuffer.position(0)
        GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        val target = if (isOes) android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES else GLES20.GL_TEXTURE_2D
        GLES20.glBindTexture(target, textureId)
        program.setUniform1i("uTexture", 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(posLoc)
        GLES20.glDisableVertexAttribArray(texLoc)
    }

    fun release() {
        oesProgram?.release()
        tex2dProgram?.release()
    }
}
