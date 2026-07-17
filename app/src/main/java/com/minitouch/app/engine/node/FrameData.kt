package com.minitouch.app.engine.node

import com.minitouch.app.hands.HandResult

/**
 * O "pacote" que flui entre nós do grafo a cada frame.
 *
 * textureId: textura OpenGL (GL_TEXTURE_2D ou OES) contendo a imagem do frame.
 *            0 significa "sem textura" (nó que só produz metadados, ex: HandTrackingNode).
 * width/height: dimensões da textura.
 * isOes: true se a textura é GL_TEXTURE_EXTERNAL_OES (vem direto da câmera).
 * timestampNs: timestamp do frame original, usado para sincronizar o encoder.
 * hands: resultado mais recente de detecção de mãos (pode ser de um frame anterior,
 *        já que a IA roda assíncrona e pode ser mais lenta que a câmera).
 */
data class FrameData(
    val textureId: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val isOes: Boolean = false,
    val timestampNs: Long = 0L,
    val hands: HandResult? = null,
)
