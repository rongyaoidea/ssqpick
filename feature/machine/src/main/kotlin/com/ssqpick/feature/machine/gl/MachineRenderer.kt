package com.ssqpick.feature.machine.gl

import android.opengl.GLES30
import android.opengl.Matrix
import com.ssqpick.feature.machine.physics.Ball
import com.ssqpick.feature.machine.MachineLayout
import com.ssqpick.feature.machine.physics.DrumWorld

/** 一台鼓的渲染配置。槽位布局集中在 [MachineLayout]，避免两处各写一遍。 */
data class DrumRenderSpec(
    /** 鼓心在渲染坐标系里的位置（物理模拟始终在鼓心为原点的局部系里做）。 */
    val centerX: Float,
    val centerY: Float,
    val centerZ: Float,
    val drumRadius: Float,
    val ballScale: Float,
    val ballCount: Int,
    /** 出口展示槽数量：红鼓 6 个，蓝鼓 1 个。 */
    val slotCount: Int,
    /** 相邻展示槽的间距（球不重叠）。 */
    val slotSpacing: Float,
    val kind: Kind,
) {
    enum class Kind { RED, BLUE }
}

/**
 * 摇号机渲染器。
 *
 * 一次 [onDrawFrame] 完成：清屏 → 布光相机 → 实例化画全部球（49 个一次 draw call）
 * → 半透明画两个鼓壳。球数固定、无逐帧分配，60fps 下每帧只有两次实例化绘制。
 */
class MachineRenderer(
    private val redWorld: DrumWorld,
    private val blueWorld: DrumWorld,
) : android.opengl.GLSurfaceView.Renderer {

    private var ballProgram = 0
    private var shellProgram = 0

    private lateinit var mesh: SphereMesh
    private var vboPosition = 0
    private var vboNormal = 0
    private var vboUv = 0
    private var ibo = 0
    private var instanceVbo = 0
    private var atlasId = 0

    private val view = FloatArray(16)
    private val proj = FloatArray(16)
    private val viewProj = FloatArray(16)
    private val model = FloatArray(16)
    private val scratch = FloatArray(16)

    private val instanceData = FloatArray(MAX_BALLS * 24)
    private val instanceBuffer: java.nio.FloatBuffer =
        java.nio.ByteBuffer.allocateDirect(MAX_BALLS * 24 * 4)
            .order(java.nio.ByteOrder.nativeOrder())
            .asFloatBuffer()

    private val specRed = MachineLayout.RED
    private val specBlue = MachineLayout.BLUE

    private var cameraPos = floatArrayOf(0f, 1.35f, 7.1f)

    /** 鼓壳累计转角，纯表现用。 */
    private var shellAngle = 0f

    override fun onSurfaceCreated(gl: javax.microedition.khronos.opengles.GL10?, config: javax.microedition.khronos.egl.EGLConfig?) {
        GLES30.glClearColor(0.055f, 0.063f, 0.086f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_BACK)

        ballProgram = GlUtil.buildProgram(BALL_VERT, BALL_FRAG)
        shellProgram = GlUtil.buildProgram(SHELL_VERT, SHELL_FRAG)

        mesh = SphereMesh()
        vboPosition = SphereMesh.createVbo(mesh.vertexBuffer)
        vboNormal = SphereMesh.createVbo(mesh.normalBuffer)
        vboUv = SphereMesh.createVbo(mesh.uvBuffer)
        ibo = SphereMesh.createIbo(mesh.indexBuffer)

        val ids = IntArray(1)
        GLES30.glGenBuffers(1, ids, 0)
        instanceVbo = ids[0]
        atlasId = BallTextures.atlasTextureId()
    }

    override fun onSurfaceChanged(gl: javax.microedition.khronos.opengles.GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        val aspect = if (height == 0) 1f else width.toFloat() / height.toFloat()
        Matrix.perspectiveM(proj, 0, 44f, aspect, 0.1f, 60f)
    }

    override fun onDrawFrame(gl: javax.microedition.khronos.opengles.GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        val eye = cameraPos
        val target = floatArrayOf(0f, 0.12f, 0f)
        val up = floatArrayOf(0f, 1f, 0f)
        Matrix.setLookAtM(view, 0, eye[0], eye[1], eye[2], target[0], target[1], target[2], up[0], up[1], up[2])
        Matrix.multiplyMM(viewProj, 0, proj, 0, view, 0)

        var count = 0
        count = appendInstances(count, specRed, redWorld.balls)
        count = appendInstances(count, specBlue, blueWorld.balls)
        if (count > 0) drawInstanced(count)

        drawTray(specRed)
        drawTray(specBlue)
        drawShell(specRed, redWorld.omega)
        drawShell(specBlue, blueWorld.omega)
    }

    /** 把一台鼓的球写入实例缓冲，返回新的写入位置（以球计）。 */
    private fun appendInstances(offset: Int, spec: DrumRenderSpec, balls: List<Ball>): Int {
        var o = offset
        for (i in 0 until minOf(balls.size, spec.ballCount)) {
            if (o >= MAX_BALLS) return o
            val b = balls[i]
            Mat4.model(model, spec.centerX + b.x, spec.centerY + b.y, spec.centerZ + b.z, spec.ballScale, b.qy, b.qx)

            val uv = BallTextures.cellUv(atlasCell(spec.kind, b.number))
            val base = o * 24
            System.arraycopy(model, 0, instanceData, base, 16)
            // 位置 16..18 是 tint（红白/蓝白球的底色微调），19..22 是图集 UV 矩形
            instanceData[base + 16] = 1f
            instanceData[base + 17] = 1f
            instanceData[base + 18] = 1f
            instanceData[base + 19] = uv[0]
            instanceData[base + 20] = uv[1]
            instanceData[base + 21] = uv[2]
            instanceData[base + 22] = uv[3]
            instanceData[base + 23] = 0f
            o++
        }
        return o
    }

    private fun atlasCell(kind: DrumRenderSpec.Kind, number: Int): Int =
        if (kind == DrumRenderSpec.Kind.RED) (number - 1).coerceIn(0, 32)
        else 33 + (number - 1).coerceIn(0, 15)

    private fun drawInstanced(count: Int) {
        GLES30.glUseProgram(ballProgram)

        instanceBuffer.clear()
        instanceBuffer.put(instanceData, 0, count * 24)
        instanceBuffer.position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instanceVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, count * 24 * 4, instanceBuffer, GLES30.GL_DYNAMIC_DRAW)

        val stride = 24 * 4
        for (r in 0 until 4) {
            GLES30.glEnableVertexAttribArray(3 + r)
            GLES30.glVertexAttribPointer(3 + r, 4, GLES30.GL_FLOAT, false, stride, r * 16)
            GLES30.glVertexAttribDivisor(3 + r, 1)
        }
        GLES30.glEnableVertexAttribArray(7)
        GLES30.glVertexAttribPointer(7, 3, GLES30.GL_FLOAT, false, stride, 16 * 4)
        GLES30.glVertexAttribDivisor(7, 1)
        GLES30.glEnableVertexAttribArray(8)
        GLES30.glVertexAttribPointer(8, 4, GLES30.GL_FLOAT, false, stride, 19 * 4)
        GLES30.glVertexAttribDivisor(8, 1)

        bindMeshAttribs()

        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(ballProgram, "uViewProj"), 1, false, viewProj, 0)
        GLES30.glUniform3f(
            GLES30.glGetUniformLocation(ballProgram, "uCameraPos"),
            cameraPos[0], cameraPos[1], cameraPos[2],
        )
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, atlasId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(ballProgram, "uAtlas"), 0)

        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES30.glDrawElementsInstanced(GLES30.GL_TRIANGLES, mesh.indexCount, GLES30.GL_UNSIGNED_INT, 0, count)

        for (loc in 3..8) {
            GLES30.glVertexAttribDivisor(loc, 0)
            GLES30.glDisableVertexAttribArray(loc)
        }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    /** 出口展示托盘：用压扁的球网格当暗色托盘，避免再引入一套几何资源。 */
    private fun drawTray(spec: DrumRenderSpec) {
        val width = maxOf(spec.drumRadius * 0.95f, spec.drumRadius * 2.3f)
        val y = MachineLayout.slotTarget(spec, 0)[1] + spec.centerY
        Mat4.identity(model)
        Matrix.translateM(model, 0, spec.centerX, y, -spec.ballScale * 1.4f)
        Matrix.scaleM(model, 0, width, spec.ballScale * 0.5f, spec.drumRadius * 0.42f)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glUseProgram(shellProgram)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(shellProgram, "uModel"), 1, false, model, 0)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(shellProgram, "uViewProj"), 1, false, viewProj, 0)
        GLES30.glUniform3f(
            GLES30.glGetUniformLocation(shellProgram, "uCameraPos"),
            cameraPos[0], cameraPos[1], cameraPos[2],
        )
        GLES30.glUniform4f(GLES30.glGetUniformLocation(shellProgram, "uColor"), 0.10f, 0.12f, 0.17f, 0.85f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(shellProgram, "uFresnelPower"), 3f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(shellProgram, "uGridAmount"), 0.02f)
        bindMeshAttribs()
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, mesh.indexCount, GLES30.GL_UNSIGNED_INT, 0)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    private fun bindMeshAttribs() {
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboPosition)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboNormal)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboUv)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, 0, 0)
    }

    private fun drawShell(spec: DrumRenderSpec, omega: Float) {
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)

        GLES30.glUseProgram(shellProgram)
        shellAngle = (shellAngle + omega * 0.016f) % 360f
        Mat4.model(model, spec.centerX, spec.centerY, spec.centerZ, spec.drumRadius, shellAngle, 0f)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(shellProgram, "uModel"), 1, false, model, 0)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(shellProgram, "uViewProj"), 1, false, viewProj, 0)
        GLES30.glUniform3f(
            GLES30.glGetUniformLocation(shellProgram, "uCameraPos"),
            cameraPos[0], cameraPos[1], cameraPos[2],
        )
        GLES30.glUniform4f(GLES30.glGetUniformLocation(shellProgram, "uColor"), 0.55f, 0.72f, 0.95f, 0.13f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(shellProgram, "uFresnelPower"), 2.4f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(shellProgram, "uGridAmount"), 0.10f)

        bindMeshAttribs()
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, mesh.indexCount, GLES30.GL_UNSIGNED_INT, 0)

        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    companion object {
        const val MAX_BALLS = 64
    }
}
