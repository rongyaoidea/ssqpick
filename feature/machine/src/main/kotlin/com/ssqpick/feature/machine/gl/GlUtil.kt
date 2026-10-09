package com.ssqpick.feature.machine.gl

import android.opengl.GLES30
import android.util.Log

/** GLSL 编译与程序链接的小工具，带完整错误信息（着色器报错不带上下文最难排查）。 */
object GlUtil {

    private const val TAG = "GlUtil"

    fun compileShader(type: Int, source: String): Int {
        val id = GLES30.glCreateShader(type)
        GLES30.glShaderSource(id, source)
        GLES30.glCompileShader(id)
        val status = IntArray(1)
        GLES30.glGetShaderiv(id, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(id)
            GLES30.glDeleteShader(id)
            throw RuntimeException("着色器编译失败 ($type): $log\n源码:\n$source")
        }
        return id
    }

    fun buildProgram(vertSource: String, fragSource: String): Int {
        val vs = compileShader(GLES30.GL_VERTEX_SHADER, vertSource)
        val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fragSource)
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vs)
        GLES30.glAttachShader(program, fs)
        GLES30.glLinkProgram(program)
        val status = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(program)
            GLES30.glDeleteProgram(program)
            GLES30.glDeleteShader(vs)
            GLES30.glDeleteShader(fs)
            throw RuntimeException("着色器程序链接失败: $log")
        }
        // 着色器对象链接后即可释放
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        Log.d(TAG, "shader program $program built")
        return program
    }
}
