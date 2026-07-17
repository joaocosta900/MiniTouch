package com.minitouch.app.engine.nodes

import android.graphics.SurfaceTexture
import com.minitouch.app.engine.gl.GlTextures
import com.minitouch.app.engine.node.FrameData
import com.minitouch.app.engine.node.Node

/**
 * Primeiro nó do grafo. Cria a textura OES e o SurfaceTexture que o CameraX escreve.
 * A cada process(), chama updateTexImage() para pegar o frame mais recente da câmera.
 */
class CameraInputNode(
    override val id: String = "camera_input",
    val width: Int,
    val height: Int,
) : Node {

    var textureId: Int = 0
        private set
    lateinit var surfaceTexture: SurfaceTexture
        private set

    private val transformMatrix = FloatArray(16)

    override fun onCreate() {
        textureId = GlTextures.createOesTexture()
        surfaceTexture = SurfaceTexture(textureId)
    }

    /** Chamado pelo PipelineManager quando o listener onFrameAvailable dispara. */
    fun markFrameAvailable() {
        pendingFrame = true
    }

    @Volatile
    private var pendingFrame = false

    override fun process(inputs: List<FrameData>): FrameData {
        if (pendingFrame) {
            surfaceTexture.updateTexImage()
            surfaceTexture.getTransformMatrix(transformMatrix)
            pendingFrame = false
        }
        return FrameData(
            textureId = textureId,
            width = width,
            height = height,
            isOes = true,
            timestampNs = surfaceTexture.timestamp,
        )
    }

    override fun onDestroy() {
        surfaceTexture.release()
        GlTextures.deleteTexture(textureId)
    }
}
