package com.ssqpick.feature.machine

import android.content.Context
import android.opengl.GLSurfaceView
import com.ssqpick.feature.machine.gl.MachineRenderer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 摇号机的 GL 视图。
 *
 * 声明需要 OpenGL ES 3.0（实例化绘制与 GLSL 300 es 需要它）。
 * 渲染持续模式，由控制器推进物理；暂停时停止回调，避免后台白耗电。
 */
class MachineView(context: Context, private val controller: MachineController) : GLSurfaceView(context) {

    private val renderer: MachineRenderer =
        MachineRenderer(controller.redWorld, controller.blueWorld)

    private var lastNanos = 0L

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        preserveEGLContextOnPause = true
        setRenderer(object : Renderer {
            override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
                renderer.onSurfaceCreated(gl, config)
                lastNanos = 0L
            }

            override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
                renderer.onSurfaceChanged(gl, width, height)
            }

            override fun onDrawFrame(gl: GL10?) {
                val now = System.nanoTime()
                if (lastNanos == 0L) lastNanos = now
                // 夹住 dt：从后台切回来时，不会因为一个巨大的 dt 把球甩出鼓外
                val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                lastNanos = now
                controller.advance(dt)
                renderer.onDrawFrame(gl)
            }
        })
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onPause() {
        super.onPause()
        lastNanos = 0L
    }
}
