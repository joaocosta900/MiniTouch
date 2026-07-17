package com.minitouch.app.engine.node

import android.util.Log

/**
 * Grafo de execução de nós.
 *
 * Uso:
 *   val graph = NodeGraph()
 *   graph.addNode(cameraNode)
 *   graph.addNode(handNode, dependsOn = listOf(cameraNode.id))
 *   graph.addNode(shaderNode, dependsOn = listOf(cameraNode.id))
 *   graph.addNode(overlayNode, dependsOn = listOf(shaderNode.id, handNode.id))
 *   graph.addNode(outputNode, dependsOn = listOf(overlayNode.id))
 *   graph.build()
 *   ...
 *   val finalFrame = graph.renderFrame() // chamado a cada frame na GL thread
 */
class NodeGraph {
    private data class Entry(val node: Node, val dependsOn: List<String>)

    private val entries = LinkedHashMap<String, Entry>()
    private var executionOrder: List<Entry> = emptyList()
    private var terminalNodeId: String? = null

    fun addNode(node: Node, dependsOn: List<String> = emptyList()) {
        require(!entries.containsKey(node.id)) { "Nó duplicado: ${node.id}" }
        entries[node.id] = Entry(node, dependsOn)
    }

    /**
     * Calcula a ordem topológica (Kahn's algorithm) e chama onCreate() em cada nó.
     * O último nó adicionado sem nenhum outro nó dependendo dele é considerado o "terminal"
     * (o output final do grafo), a menos que setTerminal() seja chamado explicitamente.
     */
    fun build(terminalId: String? = null) {
        val inDegree = entries.mapValues { 0 }.toMutableMap()
        val dependents = entries.mapValues { mutableListOf<String>() }
        for ((id, entry) in entries) {
            for (dep in entry.dependsOn) {
                require(entries.containsKey(dep)) { "Nó '$id' depende de '$dep', que não existe" }
                dependents[dep]!!.add(id)
                inDegree[id] = inDegree[id]!! + 1
            }
        }

        val queue = ArrayDeque(inDegree.filterValues { it == 0 }.keys)
        val order = mutableListOf<Entry>()
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            order += entries[id]!!
            for (dep in dependents[id]!!) {
                inDegree[dep] = inDegree[dep]!! - 1
                if (inDegree[dep] == 0) queue.addLast(dep)
            }
        }
        check(order.size == entries.size) { "Ciclo detectado no grafo de nós" }

        executionOrder = order
        val hasDependents = dependents.filterValues { it.isNotEmpty() }.keys
        terminalNodeId = terminalId ?: entries.keys.lastOrNull { it !in hasDependents }

        for (entry in order) entry.node.onCreate()
        Log.i(TAG, "Grafo construído: ${order.joinToString(" -> ") { it.node.id }}")
    }

    /** Executa todos os nós na ordem correta e retorna o FrameData do nó terminal. */
    fun renderFrame(): FrameData {
        val outputs = HashMap<String, FrameData>()
        var terminalOutput = FrameData()
        for (entry in executionOrder) {
            val inputs = entry.dependsOn.map { outputs[it] ?: FrameData() }
            val result = entry.node.process(inputs)
            outputs[entry.node.id] = result
            if (entry.node.id == terminalNodeId) terminalOutput = result
        }
        return terminalOutput
    }

    fun destroy() {
        for (entry in executionOrder) entry.node.onDestroy()
        entries.clear()
        executionOrder = emptyList()
    }

    companion object {
        private const val TAG = "NodeGraph"
    }
}
