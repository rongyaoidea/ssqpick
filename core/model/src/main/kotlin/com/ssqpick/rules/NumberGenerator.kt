package com.ssqpick.rules

import com.ssqpick.model.Bet
import java.security.SecureRandom

/**
 * 号码生成器。
 *
 * 选号只用 CSPRNG（[SecureRandom]），保证每个 6+1 组合等概率出现。
 * 3D 摇号机只是表现层——它负责把结果"演"出来，不参与决定结果。
 * 这一点很重要：物理引擎的碰撞动力学不是均匀随机源，直接拿它当选号器
 * 会让号码分布带偏差，反而破坏了彩票本身应有的公平性。
 */
object NumberGenerator {

    private val random = SecureRandom()

    /** 生成一注号码（红球升序）。 */
    fun generateBet(): Bet = Bet(sample(TicketRules.RED_TOTAL, TicketRules.RED_PICK), listOf(1 + random.nextInt(TicketRules.BLUE_TOTAL)))

    /** 从 1..total 中无放回均匀抽取 [count] 个，升序返回。 */
    fun sample(total: Int, count: Int): List<Int> {
        require(count in 0..total) { "count=$count 超出 1..$total" }
        val pool = IntArray(total) { it + 1 }
        // 部分 Fisher-Yates：前 count 个位置完成洗牌
        for (i in 0 until count) {
            val j = i + random.nextInt(total - i)
            val tmp = pool[i]
            pool[i] = pool[j]
            pool[j] = tmp
        }
        pool.sort(0, count)
        return pool.take(count)
    }
}
