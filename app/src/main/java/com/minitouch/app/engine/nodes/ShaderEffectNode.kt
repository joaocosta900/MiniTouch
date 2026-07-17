package com.minitouch.app.engine.nodes

import android.opengl.GLES20
import com.minitouch.app.engine.gl.FullFrameQuad
import com.minitouch.app.engine.gl.GlTextures
import com.minitouch.app.engine.gl.ShaderProgram
import com.minitouch.app.engine.node.FrameData
import com.minitouch.app.engine.node.Node

private const val VERTEX_SHADER = """
    attribute vec4 aPosition;
    attribute vec2 aTexCoord;
    varying vec2 vTexCoord;
    void main() {
        gl_Position = aPosition;
        vTexCoord = aTexCoord;
    }
"""

/** Exemplo de efeito: leve realce de saturação/contraste. Troque à vontade. */
private const val DEFAULT_FRAGMENT_SHADER = """
    #extension GL_OES_EGL_image_external : require
    precision mediump float;
    varying vec2 vTexCoord;
    uniform samplerExternalOES uTexture;
    uniform float uSaturation;
    void main() {
        vec4 color = texture2D(uTexture, vTexCoord);
        float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
        vec3 result = mix(vec3(gray), color.rgb, uSaturation);
        gl_FragColor = vec4(result, color.a);
    }
"""

/**
 * Renderiza a textura OES de entrada dentro de um framebuffer, aplicando um shader
 * customizado, e produz uma textura 2D comum de saída (mais fácil de compor com outros
 * nós, e o que HandTrackingNode/OutputNode esperam).
 */
class ShaderEffectNode(
    override val id: String = "shader_effect",
    private val width: Int,
    private val height: Int,
    fragmentShader: String = DEFAULT_FRAGMENT_SHADER,
    var saturation: Float = 1.0f,
) : Node {

    private val quad = FullFrameQuad()
    private lateinit var program: ShaderProgram
    private var outputTexture = 0
    private var fbo = 0
    private val vertexShaderSrc = VERTEX_SHADER
    private val fragmentShaderSrc = fragmentShader

    override fun onCreate() {
        program = ShaderProgram(vertexShaderSrc, fragmentShaderSrc)
        outputTexture = GlTextures.createTexture2D(width, height)
        fbo = GlTextures.createFramebuffer(outputTexture)
    }

    override fun process(inputs: List<FrameData>): FrameData {
        val input = inputs.firstOrNull() ?: return FrameData()
        if (input.textureId == 0) return input

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glViewport(0, 0, width, height)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        program.use()
        program.setUniform1f("uSaturation", saturation)
        quad.drawWithShader(program, input.textureId, isOes = input.isOes)

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

        return input.copy(textureId = outputTexture, isOes = false, width = width, height = height)
    }

    override fun onDestroy() {
        program.release()
        quad.release()
        GlTextures.deleteFramebuffer(fbo)
        GlTextures.deleteTexture(outputTexture)
    }
}
