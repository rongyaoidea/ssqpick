package com.ssqpick.feature.machine.gl

import android.opengl.GLES30
import java.nio.FloatBuffer

/**
 * 单位球网格（UV 球），供实例化渲染复用。
 *
 * 只生成一次：33 个红球 + 16 个蓝球共用一个网格，靠每实例的模型矩阵与颜色区分。
 * 三角形绕序为顺时针（从外侧看），默认关闭背面剔除也无所谓，但开上更省。
 */
class SphereMesh(slices: Int = 24, rings: Int = 16) {

    val vertexBuffer: FloatBuffer
    val normalBuffer: FloatBuffer
    val uvBuffer: FloatBuffer
    val indexBuffer: java.nio.IntBuffer
    val indexCount: Int

    init {
        val verts = ArrayList<Float>()
        val normals = ArrayList<Float>()
        val uvs = ArrayList<Float>()

        for (r in 0..rings) {
            val v = r.toFloat() / rings
            val phi = v * Math.PI
            val sp = Math.sin(phi)
            val cp = Math.cos(phi)
            for (s in 0..slices) {
                val u = s.toFloat() / slices
                val theta = u * 2 * Math.PI
                val sr = Math.sin(theta)
                val cr = Math.cos(theta)
                val x = (sp * cr).toFloat()
                val y = cp.toFloat()
                val z = (sp * sr).toFloat()
                verts += x
                verts += y
                verts += z
                normals += x
                normals += y
                normals += z
                uvs += u
                uvs += 1f - v
            }
        }

        val indices = ArrayList<Int>()
        for (r in 0 until rings) {
            for (s in 0 until slices) {
                val a = r * (slices + 1) + s
                val b = a + slices + 1
                indices += a
                indices += b
                indices += a + 1
                indices += a + 1
                indices += b
                indices += b + 1
            }
        }

        vertexBuffer = floatBuffer(verts.toFloatArray())
        normalBuffer = floatBuffer(normals.toFloatArray())
        uvBuffer = floatBuffer(uvs.toFloatArray())
        indexBuffer = java.nio.IntBuffer.allocate(indices.size).apply {
            indices.forEach { put(it) }
            position(0)
        }
        indexCount = indices.size
    }

    fun destroy() {
        // VAO/VBO 由 MachineRenderer 持有，这里只提供接口对称性
    }

    companion object {
        fun createVbo(buffer: FloatBuffer): Int {
            val ids = IntArray(1)
            GLES30.glGenBuffers(1, ids, 0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, ids[0])
            GLES30.glBufferData(
                GLES30.GL_ARRAY_BUFFER,
                buffer.capacity() * 4,
                buffer,
                GLES30.GL_STATIC_DRAW,
            )
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
            return ids[0]
        }

        fun createIbo(buffer: java.nio.IntBuffer): Int {
            val ids = IntArray(1)
            GLES30.glGenBuffers(1, ids, 0)
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ids[0])
            GLES30.glBufferData(
                GLES30.GL_ELEMENT_ARRAY_BUFFER,
                buffer.capacity() * 4,
                buffer,
                GLES30.GL_STATIC_DRAW,
            )
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
            return ids[0]
        }
    }
}
