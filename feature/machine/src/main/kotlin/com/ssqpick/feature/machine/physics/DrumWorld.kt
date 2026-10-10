package com.ssqpick.feature.machine.physics

import kotlin.math.sqrt

/** 三维向量运算的小工具，避免在热循环里分配对象。 */
object V3 {
    fun len(x: Float, y: Float, z: Float) = sqrt(x * x + y * y + z * z)
}

/**
 * 一个摇奖球。
 *
 * [escaping] 置真后该球不再与鼓壁碰撞，改为受 [attractX/Y/Z] 牵引滑向出口槽，
 * 这样"被选中的球飞出"是物理上可解释的动画，而不是瞬移。
 */
class Ball(
    var number: Int = 1,
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var vz: Float = 0f,
    /** 自转角速度，纯表现用。 */
    var spinX: Float = 0f,
    var spinY: Float = 0f,
    var escaping: Boolean = false,
    var attractX: Float = 0f,
    var attractY: Float = 0f,
    var attractZ: Float = 0f,
    /** 该球是否已就位于出口展示槽。 */
    var settled: Boolean = false,
    /** 渲染用的累计自转角度（表现层读取）。 */
    var qx: Float = 0f,
    var qy: Float = 0f,
) {
    val speed get() = V3.len(vx, vy, vz)
}

/**
 * 摇号机鼓内的物理世界。
 *
 * 坐标系：鼓心为原点，旋转轴为 Y 轴，模拟始终在**鼓自身的旋转坐标系**里进行。
 * 这样鼓壁静止、球感受到惯性力，数学上比"旋转一个静态球容器"简单且稳定得多：
 *
 * ```
 * a_离心 = ω² · r_perp        （沿径向向外推开）
 * a_科氏 = −2 · (ω × v)       （ω = (0, ω, 0)，使球产生螺旋轨迹）
 * a_重力 = g                  （沿 −Y，制造沉降与翻滚）
 * ```
 *
 * 球球碰撞取等质量完全弹性系数的迭代表彰式求解，分离位置避免重叠；
 * 鼓壁碰撞做径向投影 + 法向反弹 + 切向带动（把球的切向速度推向壁面速度 ω×r，
 * 这样球才会被真的"搅"起来）。
 */
class DrumWorld(
    val drumRadius: Float = 1f,
    val ballRadius: Float = 0.26f,
    var omega: Float = 5.5f,
    val gravity: Float = -2.2f,
    val restitution: Float = 0.62f,
    val wallRestitution: Float = 0.42f,
    /** 稳定性上限，超过会被截断，防止穿透。 */
    val maxSpeed: Float = 14f,
) {
    val balls = ArrayList<Ball>()

    /** 统计量，供测试与 UI 读数。 */
    var stepCount: Long = 0L
        private set
    var maxPenetration: Float = 0f
        private set

    /** 累计转角（度）。纯表现用：2D 舞台靠它画出随鼓旋转的刻度。 */
    private var angle = 0f

    fun angleDegrees(): Float = angle

    fun addBalls(count: Int, numberOffset: Int = 1) {
        repeat(count) { balls += Ball(number = it + numberOffset) }
    }

    /** 把球随机撒进鼓内（不重叠）。 */
    fun scatter(random: kotlin.random.Random = kotlin.random.Random.Default) {
        for (b in balls) {
            var tries = 0
            do {
                val r = drumRadius - ballRadius - 0.02f
                val u = random.nextDouble() * 2 - 1
                val phi = random.nextDouble() * 2 * Math.PI
                val s = sqrt(1 - u * u)
                b.x = (r * s * kotlin.math.cos(phi)).toFloat()
                b.y = (r * u).toFloat()
                b.z = (r * s * kotlin.math.sin(phi)).toFloat()
                b.vx = 0f
                b.vy = 0f
                b.vz = 0f
                tries++
            } while (tries < 60 && balls.any { it !== b && overlaps(it, b) })
            b.escaping = false
            b.settled = false
        }
    }

    private fun overlaps(a: Ball, b: Ball): Boolean {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dz = a.z - b.z
        val min = ballRadius * 2
        return dx * dx + dy * dy + dz * dz < min * min
    }

    /**
     * 推进一个物理帧。内部按固定步长切分，保证不同帧率下行为一致。
     */
    fun step(dt: Float) {
        var remaining = dt.coerceIn(0f, 0.25f)
        var guard = 0
        while (remaining > 1e-6f && guard < 64) {
            val h = minOf(FIXED_DT, remaining)
            integrate(h)
            collideBalls()
            collideWall()
            remaining -= h
            guard++
            stepCount++
            angle = (angle + Math.toDegrees(omega.toDouble() * h.toDouble()).toFloat()) % 360f
        }
    }

    private fun integrate(h: Float) {
        val w = omega
        val w2 = w * w
        for (b in balls) {
            if (b.settled) {
                b.vx = 0f
                b.vy = 0f
                b.vz = 0f
                continue
            }
            if (b.escaping) {
                // 牵引阶段：线性弹簧拖向目标点，同时保留阻尼
                val k = 26f
                val d = 5.5f
                b.vx += (b.attractX - b.x) * k * h - b.vx * d * h
                b.vy += (b.attractY - b.y) * k * h - b.vy * d * h
                b.vz += (b.attractZ - b.z) * k * h - b.vz * d * h
            } else {
                val ax = w2 * b.x - 2f * w * b.vz
                val az = w2 * b.z + 2f * w * b.vx
                b.vx += ax * h
                b.vy += gravity * h
                b.vz += az * h
            }
            val sp = b.speed
            if (sp > maxSpeed) {
                val s = maxSpeed / sp
                b.vx *= s
                b.vy *= s
                b.vz *= s
            }
            b.x += b.vx * h
            b.y += b.vy * h
            b.z += b.vz * h
            b.qx += b.spinX * h
            b.qy += b.spinY * h
        }
    }

    /** 等质量球球碰撞：交换法向速度 + 位置分离。 */
    private fun collideBalls() {
        val min = ballRadius * 2
        val n = balls.size
        for (i in 0 until n) {
            val a = balls[i]
            for (j in i + 1 until n) {
                val b = balls[j]
                if (a.settled && b.settled) continue
                if (a.escaping && b.escaping) continue
                var dx = b.x - a.x
                var dy = b.y - a.y
                var dz = b.z - a.z
                var d2 = dx * dx + dy * dy + dz * dz
                if (d2 >= min * min || d2 <= 1e-12f) continue
                val d = sqrt(d2)
                val nx = dx / d
                val ny = dy / d
                val nz = dz / d

                // 位置分离，各退一半
                val overlap = min - d
                if (!a.escaping && !a.settled) {
                    a.x -= nx * overlap * 0.5f
                    a.y -= ny * overlap * 0.5f
                    a.z -= nz * overlap * 0.5f
                }
                if (!b.escaping && !b.settled) {
                    b.x += nx * overlap * 0.5f
                    b.y += ny * overlap * 0.5f
                    b.z += nz * overlap * 0.5f
                }

                // 相对速度沿法向的分量
                val rvn = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny + (b.vz - a.vz) * nz
                if (rvn > 0f) continue
                val e = restitution
                val jimp = -(1f + e) * rvn * 0.5f
                if (a.escaping || b.escaping) {
                    // 逃逸中的球视为质量无穷大，不被撞开
                    val solid = if (a.escaping) b else a
                    val other = if (a.escaping) a else b
                    val rr = (other.vx - solid.vx) * nx + (other.vy - solid.vy) * ny + (other.vz - solid.vz) * nz
                    if (rr < 0f) {
                        val jj = -(1f + e) * rr
                        solid.vx += jj * nx
                        solid.vy += jj * ny
                        solid.vz += jj * nz
                    }
                } else {
                    a.vx -= jimp * nx
                    a.vy -= jimp * ny
                    a.vz -= jimp * nz
                    b.vx += jimp * nx
                    b.vy += jimp * ny
                    b.vz += jimp * nz
                }
            }
        }
    }

    /** 鼓壁：径向投影 + 反弹 + 切向带动。 */
    private fun collideWall() {
        val limit = drumRadius - ballRadius
        for (b in balls) {
            if (b.escaping || b.settled) continue
            val d = V3.len(b.x, b.y, b.z)
            if (d <= limit || d <= 1e-6f) continue
            val nx = b.x / d
            val ny = b.y / d
            val nz = b.z / d
            b.x = nx * limit
            b.y = ny * limit
            b.z = nz * limit

            val vn = b.vx * nx + b.vy * ny + b.vz * nz
            if (vn <= 0f) {
                b.vx -= (1f + wallRestitution) * vn * nx
                b.vy -= (1f + wallRestitution) * vn * ny
                b.vz -= (1f + wallRestitution) * vn * nz
            }
            // 壁面切向速度 = ω × r（ω 沿 Y）
            val wx = omega * b.z
            val wz = -omega * b.x
            // 把切向速度往壁面速度靠拢，制造搅动
            val blend = 0.35f
            val tvx = b.vx - vn * nx
            val tvz = b.vz - vn * nz
            b.vx = tvx + (wx - tvx) * blend
            b.vz = tvz + (wz - tvz) * blend
            b.spinX = ((b.spinX * 0.9f) + (tvz * 0.6f))
            b.spinY = ((b.spinY * 0.9f) + (tvx * 0.6f))
        }
    }

    companion object {
        const val FIXED_DT = 1f / 240f
    }
}
