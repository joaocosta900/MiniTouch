package com.minitouch.app.hands

/** Um landmark normalizado (0..1) relativo ao frame da câmera. */
data class HandLandmark(val x: Float, val y: Float, val z: Float)

/** Uma mão detectada: 21 landmarks + lateralidade. */
data class Hand(
    val landmarks: List<HandLandmark>, // sempre 21 pontos, na ordem do MediaPipe
    val isRight: Boolean,
    val score: Float,
)

/** Resultado de um frame de detecção; pode conter 0, 1 ou 2 mãos. */
data class HandResult(
    val hands: List<Hand>,
    val timestampMs: Long,
)
