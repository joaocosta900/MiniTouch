package com.minitouch.app.pipeline

import android.content.Context
import android.opengl.EGLSurface
import android.os.Environment
import android.util.Size
import android.view.Surface
import androidx.lifecycle.LifecycleOwner
import com.minitouch.app.camera.CameraSource
import com.minitouch.app.engine.gl.EglCore
import com.minitouch.app.engine.gl.FullFrameQuad
import com.minitouch.app.engine.node.NodeGraph
import com.minitouch.app.engine.nodes.CameraInputNode
import com.minitouch.app.engine.nodes.HandOverlayNode
import com.minitouch.app.engine.nodes.HandTrackingNode
import com.minitouch.app.engine.nodes.OutputNode
import com.minitouch.app.engine.nodes.ShaderEffectNode
import com.minitouch.app.export.VideoEncoder
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue

/**
 * Orquestra o pipeline inteiro numa única thread com contexto EGL fixo:
 *
 *   CameraInputNode -> ShaderEffectNode -> HandOverlayNode -> OutputNode
 *                          ^                    ^
 *                          |____ HandTrackingNode (roda em paralelo, anexa `hands`)
 *
 * A thread roda um loop simples "pull": desenha um novo frame sempre que a câmera
 * avisa (onFrameAvailable) via uma fila de comandos.
 */
class PipelineManager(private val context: Context) {

    private val glThread = Executors.newSingleThreadExecutor { r -> Thread(r, "MiniTouch-GL") }
    private val commandQueue = LinkedBlockingQueue<() -> Unit>()

    private lateinit var eglCore: EglCore
    private var previewSurface: EGLSurface? = null
    private var encoderSurface: EGLSurface? = null
    private lateinit var displayQuad: FullFrameQuad

    private lateinit var graph: NodeGraph
    private lateinit var cameraNode: CameraInputNode
    private val cameraSource = CameraSource(context)

    private var videoEncoder: VideoEncoder? = null
    private var recording = false
    private var recordingStartNs = 0L

    private val width = 1280
    private val height = 720

    fun start(lifecycleOwner: LifecycleOwner, previewSurfaceProvider: () -> Surface) {
        glThread.execute {
            eglCore = EglCore()
            eglCore.start()

            val surface = previewSurfaceProvider()
            previewSurface = eglCore.createWindowSurface(surface)
            eglCore.makeCurrent(previewSurface!!)
            displayQuad = FullFrameQuad()

            buildGraph()

            cameraSource.start(
                lifecycleOwner = lifecycleOwner,
                surfaceTexture = cameraNode.surfaceTexture,
                targetSize = Size(width, height),
                executor = { command -> glThread.execute(command) },
                onFrameAvailable = { post { renderFrame() } },
            )

            runLoop()
        }
    }

    private fun buildGraph() {
        graph = NodeGraph()
        cameraNode = CameraInputNode(width = width, height = height)
        val shaderNode = ShaderEffectNode(width = width, height = height)
        val handNode = HandTrackingNode(context = context)
        val overlayNode = HandOverlayNode(width = width, height = height)
        val outputNode = OutputNode()

        graph.addNode(cameraNode)
        graph.addNode(shaderNode, dependsOn = listOf(cameraNode.id))
        graph.addNode(handNode, dependsOn = listOf(shaderNode.id))
        graph.addNode(overlayNode, dependsOn = listOf(shaderNode.id, handNode.id))
        graph.addNode(outputNode, dependsOn = listOf(overlayNode.id))
        graph.build(terminalId = outputNode.id)
    }

    /** Posta um comando para rodar na GL thread (fila simples em vez de Handler para manter zero deps extras). */
    private fun post(command: () -> Unit) = commandQueue.put(command)

    private fun runLoop() {
        while (true) {
            val command = commandQueue.take()
            command()
        }
    }

    private fun renderFrame() {
        cameraNode.markFrameAvailable()
        val result = graph.renderFrame()
        if (result.textureId == 0) return

        // Desenha na tela (preview)
        eglCore.makeCurrent(previewSurface!!)
        displayQuad.drawWithShader(displayQuad.defaultProgram2D(), result.textureId, isOes = result.isOes)
        eglCore.swapBuffers(previewSurface!!)

        // Se estiver gravando, desenha também na surface do encoder
        if (recording) {
            val encSurface = encoderSurface ?: return
            eglCore.makeCurrent(encSurface)
            displayQuad.drawWithShader(displayQuad.defaultProgram2D(), result.textureId, isOes = result.isOes)
            if (recordingStartNs == 0L) recordingStartNs = result.timestampNs
            val ptsNs = result.timestampNs - recordingStartNs
            eglCore.setPresentationTime(encSurface, ptsNs)
            eglCore.swapBuffers(encSurface)
            videoEncoder?.drainEncoder(false)
        }
    }

    fun startRecording(fileName: String = "minitouch_${System.currentTimeMillis()}.mp4") {
        post {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
            val outputFile = File(dir, fileName)

            val encoder = VideoEncoder(outputFile.absolutePath, width, height)
            encoder.start()
            videoEncoder = encoder
            encoderSurface = eglCore.createWindowSurface(encoder.inputSurface)
            recordingStartNs = 0L
            recording = true
        }
    }

    fun stopRecording(onFinished: (filePath: String?) -> Unit = {}) {
        post {
            recording = false
            videoEncoder?.drainEncoder(true)
            videoEncoder?.stop()
            encoderSurface?.let { eglCore.releaseSurface(it) }
            videoEncoder = null
            encoderSurface = null
            onFinished(null)
        }
    }

    fun stop() {
        post {
            cameraSource.stop()
            graph.destroy()
            displayQuad.release()
            previewSurface?.let { eglCore.releaseSurface(it) }
            eglCore.release()
        }
        glThread.shutdown()
    }
}
