package com.minitouch.app.engine.node

/**
 * Um nó do pipeline de processamento de vídeo.
 *
 * O grafo inteiro roda em UMA thread dedicada (a "GL thread"), porque texturas
 * OpenGL só podem ser manipuladas pelo thread dono do contexto EGL. Por isso
 * todo Node.process() é síncrono e deve ser rápido — trabalho pesado de CPU
 * (ex: MediaPipe) roda em outra thread e entrega o resultado via callback,
 * que o node consome no próximo frame (ver HandTrackingNode).
 */
interface Node {
    /** Nome único usado para logging e para achar o nó no grafo. */
    val id: String

    /** Chamado uma vez, com o contexto EGL já corrente, antes do primeiro process(). */
    fun onCreate() {}

    /**
     * Processa um frame. Recebe os outputs dos nós dos quais este depende
     * (na mesma ordem declarada em NodeGraph.addNode) e retorna seu próprio output.
     */
    fun process(inputs: List<FrameData>): FrameData

    /** Chamado quando o grafo é destruído; libere texturas/programas GL aqui. */
    fun onDestroy() {}
}
