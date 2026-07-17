package com.minitouch.app.camera

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.Size
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor

/**
 * Liga o CameraX a um SurfaceTexture já existente (criado pelo CameraInputNode
 * a partir da textura OES no contexto GL do pipeline). Assim a câmera escreve
 * direto na textura que o node engine consome — zero cópias extras.
 */
class CameraSource(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null

    fun start(
        lifecycleOwner: LifecycleOwner,
        surfaceTexture: SurfaceTexture,
        targetSize: Size,
        executor: Executor,
        lensFacing: Int = CameraSelector.LENS_FACING_BACK,
        onFrameAvailable: () -> Unit,
    ) {
        surfaceTexture.setDefaultBufferSize(targetSize.width, targetSize.height)
        surfaceTexture.setOnFrameAvailableListener({ onFrameAvailable() }, null)
        val surface = Surface(surfaceTexture)

        val providerFuture: ListenableFuture<ProcessCameraProvider> =
            ProcessCameraProvider.getInstance(context)

        providerFuture.addListener({
            val provider = providerFuture.get()
            cameraProvider = provider

            val newPreview = Preview.Builder()
                .setTargetResolution(targetSize)
                .build()
            newPreview.setSurfaceProvider(executor) { request ->
                request.provideSurface(surface, executor) { /* result callback, sem ação necessária */ }
            }

            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, selector, newPreview)
            preview = newPreview
        }, executor)
    }

    fun stop() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        preview = null
    }
}
