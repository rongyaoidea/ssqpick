package com.ssqpick.feature.machine.physics

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 三维向量运算的小工具，避免在热循环里分配对象。 */
object V3 {
    fun len(x: Float, y: Float, z: Float) = sqrt(x * x + y * y + z * z)
}

/**
 * 一个摇奖球。
 *
 * [escaping] 置真后该球不再与鼓壁碰撞，改为受 [attractX/Y/Z] 牵引沿出号路径滑动：
 * 控制器分三段给目标（管口 → 管出口 → 展示槽），[slotX/Y/Z] 是最终落点。
 */
class Ball(
    var number: Int = 1,
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var vz: Float = 0f,
    /** 绕视轴的自转角速度（rad/s）。壁面接触时由纯滚动条件决定。 */
    var spinZ: Float = 0f,
    /** 渲染用的累计自转角度（度，Compose rotate 顺时针为正）。 */
    var rollAngle: Float = 0f,
    var escaping: Boolean = false,
    /** 逃逸路径当前的牵引目标。 */
    var attractX: Float = 0f,
    var attractY: Float = 0f,
    var attractZ: Float = 0f,
    /** 逃逸的最终落点（展示槽）。渲染按它淡出。 */
    var slotX: Float = 0f,
    var slotY: Float = 0f,
    var slotZ: Float = 0f,
    /** 逃逸阶段：0=去管口，1=去管出口，2=去展示槽。 */
    var escapeStage: Int = 0,
    /** 该球是否已就位于出口展示槽。 */
    var settled: Boolean = false,
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
 * a_气流 = 湍流 + 上升 + 阻力  （鼓风机开着时才有，见 [blowerTarget]）
 * ```
 *
 * 电机与风扇都不是瞬间启停的：ω 按 [omegaTarget] 以 [omegaAccel] 渐变，
 * 风扇功率按指数趋近 [blowerTarget]。待机（两者都停）时低速球直接归零，
 * 球堆安静地落在鼓底，不再永远微抖。
 *
 * 球球碰撞取等质量恢复系数冲量 + 少量切向摩擦；鼓壁做径向投影 + 法向反弹 +
 * 切向带动，接触时按纯滚动条件（接触点速度为 0）给出自转角速度。
 */
class DrumWorld(
    val drumRadius: Float = 1f,
    val ballRadius: Float = 0.26f,
    omega: Float = 5.5f,
    val gravity: Float = -2.2f,
    val restitution: Float = 0.62f,
    val wallRestitution: Float = 0.42f,
    /** 稳定性上限，超过会被截断，防止穿透。 */
    val maxSpeed: Float = 14f,
    /** 电机角加速度（rad/s²）：真机是转起来的，不是瞬间全速。 */
    val omegaAccel: Float = 16f,
) {
    val balls = ArrayList<Ball>()

    /** 当前角速度（rad/s），由 [omegaTarget] 渐变而来。 */
    var omega: Float = omega
        private set

    /** 目标角速度：控制器按相位设置（待机 0、混合满速、出号减速）。 */
    var omegaTarget: Float = omega

    /** 鼓风机目标功率 0..1。 */
    var blowerTarget: Float = 0f

    /** 当前风扇功率（渐变趋近 [blowerTarget]）。 */
    var blower: Float = 0f
        private set

    /** 统计量，供测试与 UI 读数。 */
    var stepCount: Long = 0L
        private set
    var maxPenetration: Float = 0f
        private set

    /** 累计转角（度）。纯表现用：2D 舞台靠它画出随鼓旋转的刻度。 */
    private var angle = 0f

    /** 气流场演化的累计时间（决定湍流相位）。 */
    private var blowTime = 0f

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
            b.escapeStage = 0
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
        val frame = dt.coerceIn(0f, 0.25f)
        // 电机与风扇渐变发生在帧粒度（子步内视为常量，行为不随子步数漂移）
        omega = when {
            omegaTarget > omega -> minOf(omegaTarget, omega + omegaAccel * frame)
            omegaTarget < omega -> maxOf(omegaTarget, omega - omegaAccel * frame)
            else -> omega
        }
        blower += (blowerTarget - blower) * (frame * FAN_RAMP).coerceIn(0f, 1f)

        var remaining = frame
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
        // 电机与风扇都停了才算静止场：此时低速球直接归零（见下）
        val active = w > 0.05f || blower > 0.05f
        blowTime += h
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
                var ax = w2 * b.x - 2f * w * b.vz
                var ay = gravity
                var az = w2 * b.z + 2f * w * b.vx
                if (blower > 0.001f) {
                    val p = blower
                    // ABC 流（Arnold–Beltrami–Childress）：散度恒为零的混沌场，
                    // 只搬运不压缩，长时间吹也不会把球"攒"到某个角落
                    val kk = 2.6f
                    val ph = blowTime * 1.9f
                    val u = b.x * kk + ph
                    val v = b.y * kk - ph * 0.7f
                    val s = b.z * kk + ph * 0.5f
                    ax += (sin(s) + 0.6f * cos(v * 1.3f)) * p * TURB_AMP
                    ay += (sin(u) + 0.6f * cos(s * 1.3f)) * p * TURB_AMP
                    az += (sin(v) + 0.6f * cos(u * 1.3f)) * p * TURB_AMP
                    // 气流环：鼓底中心强上升、贴壁回落——真机鼓风机吹出的是对流。
                    // 高度因子 (R-y)/2R：底部最强、顶部归零；没有它，"向上"的力
                    // 在鼓顶会变成向外，把球全按在顶壳上（实测球会贴着球壳滑）。
                    val rp = sqrt(b.x * b.x + b.z * b.z)
                    val rn = rp / (drumRadius * 0.75f)
                    val hFactor = ((drumRadius - b.y) / drumRadius * 0.5f).coerceIn(0f, 1f)
                    ay += p * UPDRAFT_AMP * (1f - rn * rn) * hFactor
                    // 空气阻力：限制终速，也让停风后球落得干脆
                    ax -= b.vx * p * DRAG
                    ay -= b.vy * p * DRAG
                    az -= b.vz * p * DRAG
                }
                b.vx += ax * h
                b.vy += ay * h
                b.vz += az * h
                // 静止场 + 贴着壁 + 已经很慢：直接归零。
                // 要求贴壁是为了避免在自由落体的顶点把球冻在半空。
                if (!active && b.speed < SLEEP_SPEED &&
                    V3.len(b.x, b.y, b.z) > drumRadius - ballRadius - 0.06f
                ) {
                    b.vx = 0f
                    b.vy = 0f
                    b.vz = 0f
                    b.spinZ = 0f
                }
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
            // 自转：空气里缓慢衰减，壁面接触时被滚动条件覆盖
            b.spinZ *= (1f - 0.9f * h).coerceAtLeast(0f)
            b.rollAngle = (b.rollAngle - Math.toDegrees((b.spinZ * h).toDouble()).toFloat()) % 360f
        }
    }

    /** 等质量球球碰撞：交换法向速度 + 位置分离 + 一点切向摩擦。 */
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

                    // 切向摩擦：真实球不是无摩擦台球，接触会磨掉一点相对切向速度
                    val relx = b.vx - a.vx
                    val rely = b.vy - a.vy
                    val relz = b.vz - a.vz
                    val dot = relx * nx + rely * ny + relz * nz
                    val tx = relx - dot * nx
                    val ty = rely - dot * ny
                    val tz = relz - dot * nz
                    val f = FRICTION * 0.5f
                    a.vx += tx * f
                    a.vy += ty * f
                    a.vz += tz * f
                    b.vx -= tx * f
                    b.vy -= ty * f
                    b.vz -= tz * f
                }
            }
        }
    }

    /** 鼓壁：径向投影 + 反弹 + 切向带动 + 纯滚动自转。 */
    private fun collideWall() {
        val limit = drumRadius - ballRadius
        for (b in balls) {
            if (b.escaping || b.settled) continue
            val d = V3.len(b.x, b.y, b.z)
            if (d <= limit || d <= 1e-6f) continue
            if (d - limit > maxPenetration) maxPenetration = d - limit
            val nx = b.x / d
            val ny = b.y / d
            val nz = b.z / d
            b.x = nx * limit
            b.y = ny * limit
            b.z = nz * limit

            // n 是外法线：只有朝外飞（vn > 0）才需要反弹。
            // 之前写成 vn <= 0，结果向外穿透时被放行、向内时反而被加了一把力，
            // wallRestitution 实际没生效，球只会贴壁滑行。
            val vn = b.vx * nx + b.vy * ny + b.vz * nz
            if (vn > 0f) {
                b.vx -= (1f + wallRestitution) * vn * nx
                b.vy -= (1f + wallRestitution) * vn * ny
                b.vz -= (1f + wallRestitution) * vn * nz
            }
            // 壁面切向速度 = ω × r（ω 沿 Y）
            val wx = omega * b.z
            val wz = -omega * b.x
            // 只把**切向**往壁面速度靠，法向分量原样加回。
            // 之前用碰撞前的 vn 做切向分解，等于把法向残留算进切向：
            // 实测恢复系数从 0.42 变成 ≈0.92，wallRestitution 形同虚设。
            val vnAfter = b.vx * nx + b.vy * ny + b.vz * nz
            val blend = 0.35f
            var tvx = b.vx - vnAfter * nx
            var tvz = b.vz - vnAfter * nz
            tvx += (wx - tvx) * blend
            tvz += (wz - tvz) * blend
            b.vx = tvx + vnAfter * nx
            b.vz = tvz + vnAfter * nz

            // 纯滚动：接触点速度为 0 → 绕视轴自转 ω_z = (v_t × n)_z / r。
            // 屏幕上的可视旋转用 [Ball.rollAngle]，这里只给角速度。
            val tvx2 = b.vx - vnAfter * nx
            val tvy2 = b.vy - vnAfter * ny
            b.spinZ = (tvx2 * ny - tvy2 * nx) / ballRadius
        }
    }

    companion object {
        const val FIXED_DT = 1f / 240f

        /** 气流湍流幅度（加速度）。与离心/重力同量级才会真的把球吹散。 */
        private const val TURB_AMP = 10f

        /** 中心柱上升气流的最大加速度（靠壁处转为回落，形成对流环）。 */
        private const val UPDRAFT_AMP = 18f

        /** 空气阻力系数（1/s）。 */
        private const val DRAG = 0.7f

        /** 风扇功率的趋近速率（1/s）。 */
        private const val FAN_RAMP = 4.5f

        /** 静止判定速度：低于它且电机/风扇都停了，就直接归零。 */
        private const val SLEEP_SPEED = 0.06f

        /** 球间切向摩擦系数（每接触一次磨掉的比例）。 */
        private const val FRICTION = 0.12f
    }
}
