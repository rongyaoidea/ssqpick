package com.ssqpick.feature.machine.gl

import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * 极简矩阵/向量工具。
 *
 * 这里没有引入任何图形库：摇号机只需要 MVP 变换 + 法线矩阵，
 * 用 android.opengl.Matrix（平台实现，FloatArray）就够了，少一个依赖少一处兼容问题。
 */
object Mat4 {
    fun identity(out: FloatArray) = Matrix.setIdentityM(out, 0)

    fun perspective(out: FloatArray, fovY: Float, aspect: Float, near: Float, far: Float) =
        Matrix.perspectiveM(out, 0, fovY, aspect, near, far)

    fun lookAt(
        out: FloatArray,
        eye: FloatArray,
        center: FloatArray,
        up: FloatArray,
    ) = Matrix.setLookAtM(
        out, 0,
        eye[0], eye[1], eye[2],
        center[0], center[1], center[2],
        up[0], up[1], up[2],
    )

    fun multiply(out: FloatArray, lhs: FloatArray, rhs: FloatArray) = Matrix.multiplyMM(out, 0, lhs, 0, rhs, 0)

    /** 由 平移+缩放+绕 Y/X 轴旋转 组装模型矩阵。 */
    fun model(
        out: FloatArray,
        x: Float,
        y: Float,
        z: Float,
        scale: Float,
        rotY: Float,
        rotX: Float,
    ) {
        identity(out)
        Matrix.translateM(out, 0, x, y, z)
        if (rotY != 0f) Matrix.rotateM(out, 0, rotY, 0f, 1f, 0f)
        if (rotX != 0f) Matrix.rotateM(out, 0, rotX, 1f, 0f, 0f)
        Matrix.scaleM(out, 0, scale, scale, scale)
    }

    /** 4×4 矩阵 × 4 维向量。 */
    fun transform(m: FloatArray, x: Float, y: Float, z: Float): FloatArray {
        val out = FloatArray(4)
        out[0] = m[0] * x + m[4] * y + m[8] * z + m[12]
        out[1] = m[1] * x + m[5] * y + m[9] * z + m[13]
        out[2] = m[2] * x + m[6] * y + m[10] * z + m[14]
        out[3] = m[3] * x + m[7] * y + m[11] * z + m[15]
        return out
    }
}

fun floatBuffer(data: FloatArray): FloatBuffer =
    ByteBuffer.allocateDirect(data.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(data)
            position(0)
        }
