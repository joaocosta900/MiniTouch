package com.minitouch.app.hands

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

/**
 * Envolve o HandLandmarker do MediaPipe Tasks em modo LIVE_STREAM: cada frame é
 * enviado de forma assíncrona (detectAsync) e o resultado chega depois, via callback,
 * possivelmente enquanto outro frame já está sendo processado. Isso evita que a IA
 * trave o pipeline de vídeo.
 *
 * Modelo necessário: baixe "hand_landmarker.task" em
 * https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/latest/hand_landmarker.task
 * e coloque em app/src/main/assets/hand_landmarker.task
 */
class HandLandmarkerHelper(
    context: Context,
    private val onResult: (HandResult) -> Unit,
    maxHands: Int = 2,
    minDetectionConfidence: Float = 0.5f,
    useGpu: Boolean = true,
) {
    private val landmarker: HandLandmarker

    init {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("models/hand_landmarker.task")
            .setDelegate(if (useGpu) Delegate.GPU else Delegate.CPU)
            .build()

        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(maxHands)
            .setMinHandDetectionConfidence(minDetectionConfidence)
            .setMinTrackingConfidence(0.5f)
            .setResultListener(::handleResult)
            .setErrorListener { e -> android.util.Log.e(TAG, "Erro no HandLandmarker", e) }
            .build()

        landmarker = HandLandmarker.createFromOptions(context, options)
    }

    /** Envia um bitmap (extraído da textura OES via glReadPixels) para detecção assíncrona. */
    fun detectAsync(bitmap: Bitmap, timestampMs: Long) {
        val mpImage = BitmapImageBuilder(bitmap).build()
        landmarker.detectAsync(mpImage, timestampMs)
    }

    private fun handleResult(result: HandLandmarkerResult, input: com.google.mediapipe.framework.image.MPImage) {
        val hands = result.landmarks().mapIndexed { i, landmarkList ->
            val handedness = result.handednesses().getOrNull(i)?.firstOrNull()
            Hand(
                landmarks = landmarkList.map { HandLandmark(it.x(), it.y(), it.z()) },
                isRight = handedness?.categoryName() == "Right",
                score = handedness?.score() ?: 0f,
            )
        }
        onResult(HandResult(hands, result.timestampMs()))
    }

    fun close() = landmarker.close()

    companion object {
        private const val TAG = "HandLandmarkerHelper"
    }
}
