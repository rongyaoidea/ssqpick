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
        val pool = shuffledTop(total, count)
        pool.sort(0, count)
        return pool.take(count)
    }

    /**
     * 从 1..total 中无放回均匀抽取 [count] 个，返回**抽取顺序**（不排序）。
     *
     * 摇号机逐颗出号用：出号顺序必须是随机序，最后展示时再排序——
     * 如果按升序出号，"摇"的过程就露馅了（01、02、03…依次滚出来）。
     */
    fun sampleOrdered(total: Int, count: Int): List<Int> {
        require(count in 0..total) { "count=$count 超出 1..$total" }
        // 前 count 个位置本身就是洗牌结果，直接取，不排序
        return shuffledTop(total, count).take(count)
    }

    /** 部分 Fisher-Yates：把 1..total 的前 [count] 个位置洗成均匀随机序。 */
    private fun shuffledTop(total: Int, count: Int): IntArray {
        val pool = IntArray(total) { it + 1 }
        for (i in 0 until count) {
            val j = i + random.nextInt(total - i)
            val tmp = pool[i]
            pool[i] = pool[j]
            pool[j] = tmp
        }
        return pool
    }

    /**
     * 从给定候选池中无放回均匀抽取 [count] 个。
     *
     * 机选补缺用：候选池是"手选剩下的空位"，要多少补多少。
     * 池不够时截断（能补几个补几个），不断言——调用方按校验信息展示即可。
     */
    fun sampleFrom(pool: List<Int>, count: Int): List<Int> {
        val k = count.coerceIn(0, pool.size)
        if (k == 0) return emptyList()
        val idx = pool.indices.toMutableList()
        for (i in 0 until k) {
            val j = i + random.nextInt(idx.size - i)
            val tmp = idx[i]
            idx[i] = idx[j]
            idx[j] = tmp
        }
        return idx.take(k).map { pool[it] }
    }
}
