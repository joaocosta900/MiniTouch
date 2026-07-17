package com.minitouch.app.engine.nodes

import com.minitouch.app.engine.node.FrameData
import com.minitouch.app.engine.node.Node

/**
 * Nó terminal do grafo — não faz processamento, só marca qual textura é o
 * "resultado final". O PipelineManager pega o FrameData retornado por
 * graph.renderFrame() (que é o output deste nó) e o desenha tanto na
 * EGLSurface de preview quanto na EGLSurface do encoder de vídeo.
 */
class OutputNode(override val id: String = "output") : Node {
    override fun process(inputs: List<FrameData>): FrameData = inputs.firstOrNull() ?: FrameData()
}
