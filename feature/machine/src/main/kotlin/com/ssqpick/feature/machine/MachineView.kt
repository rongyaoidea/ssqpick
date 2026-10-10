package com.ssqpick.feature.machine

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.ssqpick.feature.machine.gl.MachineRenderer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 摇号机的 GL 视图。
 *
 * 声明需要 OpenGL ES 3.0（实例化绘制与 GLSL 300 es 需要它）。
 * 渲染持续模式。暂停时停止回调，避免后台白耗电。
 */
class MachineView(
    context: Context,
    private val controller: MachineController,
) : GLSurfaceView(context) {

    private val renderer: MachineRenderer =
        MachineRenderer(controller.redWorld, controller.blueWorld)

    private var lastNanos = 0L

    /** GL 初始化是否失败。失败后 [onDrawFrame] 直接跳过，不再反复抛异常。 */
    private var glFailed = false
    private var frameCount = 0L

    init {
        setEGLContextClientVersion(3)
        // 只要求 RGBA8888 + 16 位深度；不要指定 stencil，否则部分设备选不到配置
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        preserveEGLContextOnPause = true
        setRenderer(object : Renderer {
            override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
                try {
                    renderer.onSurfaceCreated(gl, config)
                } catch (e: Throwable) {
                    // 着色器编译失败、图集纹理上传失败都在这里。
                    // 不捕获的话 GL 线程会静默死掉，表现为一整块空白，极难排查。
                    glFailed = true
                    controller.onGlError(e)
                    Log.e(TAG, "GL 初始化失败，摇号机无法渲染", e)
                }
            }

            override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
                renderer.onSurfaceChanged(gl, width, height)
            }

            override fun onDrawFrame(gl: GL10?) {
                if (glFailed) return
                val now = System.nanoTime()
                if (lastNanos == 0L) lastNanos = now
                // 夹住 dt：从后台切回来时，不会因为一个巨大的 dt 把球甩出鼓外
                val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                lastNanos = now
                controller.advance(dt)
                renderer.onDrawFrame(gl)
                if (frameCount++ == 60L) {
                    Log.d(TAG, "已渲染 $frameCount 帧，物理步数 ${redWorld0().stepCount}")
                }
            }
        })
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    private fun redWorld0() = controller.redWorld

    /**
     * 视图挂载到窗口时兜底启动渲染。
     *
     * `GLSurfaceView` 的渲染线程只在 `onResume()` 后才启动，而 SurfaceView 不会自启。
     * Compose 合成开始时宿主生命周期通常已经 RESUMED，此时态监听器**不会**重放
     * ON_RESUME 事件——这正是"界面一片空白"的根因。挂载窗口是唯一必然被调用的钩子，
     * 在这里兜底最可靠。（重复调用 onResume() 是安全的）
     */
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val owner = findViewTreeLifecycleOwner()
        if (owner == null || owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            onResume()
        }
    }

    override fun onDetachedFromWindow() {
        onPause()
        super.onDetachedFromWindow()
    }

    override fun onPause() {
        super.onPause()
        lastNanos = 0L
    }

    companion object {
        private const val TAG = "MachineView"
    }
}
