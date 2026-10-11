package com.ssqpick.feature.machine

import com.ssqpick.rules.TicketRules

/**
 * 摇号机自定义数量的合法范围与联动夹取。
 *
 * 上限不是随手定的：一张票最多 [TicketRules.EXPAND_LIMIT] 注、红球最多
 * [TicketRules.MAX_RED_COMPOUND] 个、蓝球 [TicketRules.BLUE_TOTAL] 个。
 * 两个滑条因此互相牵制——红球拉大，蓝球就得变小，否则摇出来的票存不进数据库，
 * 对奖时也会被判成"注数过多"。
 *
 * 全部是纯函数，方便单测直接钉住边界。
 */
object DrawLimits {

    /** 注数上限，与票面校验同一口径。 */
    val MAX_BETS: Long = TicketRules.EXPAND_LIMIT

    val RED_MIN: Int = TicketRules.RED_PICK
    val RED_MAX: Int = TicketRules.MAX_RED_COMPOUND
    val BLUE_MIN: Int = 1
    val BLUE_MAX: Int = TicketRules.BLUE_TOTAL

    /** 红 [red] 个、蓝 [blue] 个时的注数（不校验合法性，越界由调用方先夹）。 */
    fun bets(red: Int, blue: Int): Long = TicketRules.binom(red.toLong(), TicketRules.RED_PICK.toLong()) * blue

    /** 蓝球为 [blue] 时，红球的最大合法值。 */
    fun maxRedFor(blue: Int): Int {
        val b = blue.coerceIn(BLUE_MIN, BLUE_MAX)
        // 范围一定非空：bets(6, b) = b ≤ 16 ≤ MAX_BETS
        return (RED_MIN..RED_MAX).last { bets(it, b) <= MAX_BETS }
    }

    /** 红球为 [red] 时，蓝球的最大合法值。 */
    fun maxBlueFor(red: Int): Int {
        val r = red.coerceIn(RED_MIN, RED_MAX)
        return (BLUE_MIN..BLUE_MAX).last { bets(r, it) <= MAX_BETS }
    }

    /** 调红球：红夹进合法范围，蓝被牵连时一起夹住。 */
    fun clampRed(red: Int, blue: Int): Pair<Int, Int> {
        val r = red.coerceIn(RED_MIN, RED_MAX)
        return r to blue.coerceIn(BLUE_MIN, maxBlueFor(r))
    }

    /** 调蓝球：蓝夹进合法范围，红被牵连时一起夹住。 */
    fun clampBlue(red: Int, blue: Int): Pair<Int, Int> {
        val b = blue.coerceIn(BLUE_MIN, BLUE_MAX)
        return red.coerceIn(RED_MIN, maxRedFor(b)) to b
    }
}
