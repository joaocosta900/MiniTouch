package com.minitouch.app.engine.gl

import android.opengl.GLES20

/** Compila e linka um par de shaders GLSL, expondo helpers pra uniforms/attributes. */
class ShaderProgram(vertexSrc: String, fragmentSrc: String) {
    val programId: Int = link(compile(GLES20.GL_VERTEX_SHADER, vertexSrc), compile(GLES20.GL_FRAGMENT_SHADER, fragmentSrc))

    fun use() = GLES20.glUseProgram(programId)

    fun attribLocation(name: String): Int = GLES20.glGetAttribLocation(programId, name)
    fun uniformLocation(name: String): Int = GLES20.glGetUniformLocation(programId, name)

    fun setUniform1f(name: String, v: Float) = GLES20.glUniform1f(uniformLocation(name), v)
    fun setUniform1i(name: String, v: Int) = GLES20.glUniform1i(uniformLocation(name), v)
    fun setUniformMatrix4fv(name: String, matrix: FloatArray) =
        GLES20.glUniformMatrix4fv(uniformLocation(name), 1, false, matrix, 0)

    fun release() = GLES20.glDeleteProgram(programId)

    private fun compile(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            error("Erro ao compilar shader (type=$type): $log")
        }
        return shader
    }

    private fun link(vertexShader: Int, fragmentShader: Int): Int {
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            error("Erro ao linkar shader program: $log")
        }
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        return program
    }
}
