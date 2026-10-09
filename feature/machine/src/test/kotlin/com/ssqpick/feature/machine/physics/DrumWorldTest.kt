package com.ssqpick.feature.machine.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 物理稳定性测试。
 *
 * 摇号机是纯表现层，但它必须稳定：球不能飞出鼓、不能 NaN、
 * 不能因为帧率波动炸开。这些是 GL 线程上的硬约束。
 */
class DrumWorldTest {

    private fun world(balls: Int = 33, radius: Float = 0.25f) = DrumWorld(
        drumRadius = 1f,
        ballRadius = radius,
        omega = 5.2f,
    ).apply {
        addBalls(balls)
        scatter(Random(42))
    }

    @Test
    fun `球不飞出鼓`() {
        val w = world()
        repeat(6_000) { w.step(1f / 60f) }
        val limit = w.drumRadius - w.ballRadius
        val worst = w.balls.maxOf { V3.len(it.x, it.y, it.z) }
        // 允许数值误差量级的溢出，但绝不允许真的飞出去
        assertTrue("有球跑到鼓外：最远 $worst（上限 $limit）", worst <= limit + 1e-3f)
    }

    @Test
    fun `不会出现 NaN 或无穷`() {
        val w = world()
        repeat(3_000) { w.step(1f / 60f) }
        w.balls.forEach { b ->
            assertTrue(b.x.isFinite() && b.y.isFinite() && b.z.isFinite())
            assertTrue(b.vx.isFinite() && b.vy.isFinite() && b.vz.isFinite())
        }
    }

    @Test
    fun `球之间不长期重叠`() {
        val w = world()
        repeat(2_000) { w.step(1f / 60f) }
        // 重叠不应超过球半径的一半（物理求解的收敛残差）
        var worstOverlap = 0f
        for (i in w.balls.indices) {
            for (j in i + 1 until w.balls.size) {
                val a = w.balls[i]
                val b = w.balls[j]
                val dx = a.x - b.x
                val dy = a.y - b.y
                val dz = a.z - b.z
                val d = V3.len(dx, dy, dz)
                val overlap = 2 * w.ballRadius - d
                if (overlap > worstOverlap) worstOverlap = overlap
            }
        }
        assertTrue(
            "球体重叠 $worstOverlap，超过允许的 ${w.ballRadius * 0.5f}",
            worstOverlap <= w.ballRadius * 0.5f,
        )
    }

    @Test
    fun `帧长剧烈波动不会炸开`() {
        val w = world()
        val rng = Random(7)
        repeat(1_500) {
            // 模拟掉帧：dt 在 1ms ~ 90ms 之间跳
            val dt = (rng.nextInt(1, 90)) / 1000f
            w.step(dt)
        }
        val limit = w.drumRadius - w.ballRadius
        assertTrue(w.balls.all { V3.len(it.x, it.y, it.z) <= limit + 1e-3f })
        assertTrue(w.balls.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() })
    }

    @Test
    fun `不同帧率下轨迹一致`() {
        // 相同总时长下，60fps 与 144fps 的结束位置应接近（固定步长是这个设计的目的）
        fun run(fps: Float, seconds: Float): List<Float> {
            val w = DrumWorld(drumRadius = 1f, ballRadius = 0.25f, omega = 5.2f)
            w.addBalls(6)
            w.scatter(Random(3))
            val steps = (fps * seconds).toInt()
            repeat(steps) { w.step(1f / fps) }
            return w.balls.map { V3.len(it.x, it.y, it.z) }
        }
        val a = run(60f, 2f)
        val b = run(144f, 2f)
        a.indices.forEach { i ->
            assertTrue(
                "第 $i 球 60fps=${a[i]} 144fps=${b[i]} 差异过大",
                kotlin.math.abs(a[i] - b[i]) < 0.35f,
            )
        }
    }

    @Test
    fun `逃逸球会被牵引向目标点`() {
        val w = world(balls = 6, radius = 0.25f)
        val ball = w.balls.first()
        ball.escaping = true
        ball.attractX = 0f
        ball.attractY = -1.5f
        ball.attractZ = 0f
        repeat(600) { w.step(1f / 60f) }
        assertTrue(
            "逃逸球应该被拉到目标附近，实际距离 ${V3.len(ball.x, ball.y, ball.z)}",
            V3.len(ball.x - 0f, ball.y - (-1.5f), ball.z - 0f) < 0.15f,
        )
    }

    @Test
    fun `转速为负也稳定`() {
        val w = DrumWorld(drumRadius = 1f, ballRadius = 0.25f, omega = -4f)
        w.addBalls(20)
        w.scatter(Random(9))
        repeat(2_000) { w.step(1f / 60f) }
        val limit = w.drumRadius - w.ballRadius
        assertTrue(w.balls.all { V3.len(it.x, it.y, it.z) <= limit + 1e-3f })
    }

    /** 蓝鼓只有 16 个球，装得更松，同样要稳定。 */
    @Test
    fun `蓝鼓小样本稳定`() {
        val w = world(balls = 16, radius = 0.30f)
        repeat(3_000) { w.step(1f / 60f) }
        val limit = w.drumRadius - w.ballRadius
        assertTrue(w.balls.all { V3.len(it.x, it.y, it.z) <= limit + 1e-3f })
        assertTrue(w.balls.all { it.x.isFinite() })
    }
}
