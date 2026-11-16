package com.ssqpick.rules

import com.ssqpick.model.TicketDraft
import java.security.SecureRandom
import java.util.Random
import kotlin.math.pow

/** 覆盖方案里的一注：6 个红球 + 1 个蓝球。 */
data class CoverageTicket(
    val reds: List<Int>,
    val blue: Int,
) {
    init {
        require(reds.size == 6 && reds.distinct().size == 6 && reds.all { it in 1..33 })
        require(blue in 1..16)
    }

    fun toDraft(): TicketDraft = TicketDraft(
        redPicked = reds.toSet(),
        bluePicked = setOf(blue),
    )
}

/**
 * 预算内的覆盖方案。
 *
 * [anyPrizeProbability] 是“至少中一个奖”的理论估算：
 * - 蓝球命中必得六等奖；k 注不同蓝球的命中概率是 k/16；
 * - 蓝球不中时，n 注红球里至少一注中 4 个的概率用独立近似。
 *
 * 它描述的是覆盖，不是预测；成本仍按 2 元/注线性增加，期望回报不为正。
 */
data class CoveragePlan(
    val budgetYuan: Long,
    val costYuan: Long,
    val tickets: List<CoverageTicket>,
    val anyPrizeProbability: Double,
    val coveredBlues: Int,
) {
    val fullBlueCoverage: Boolean get() = coveredBlues >= 16
    val leftoverYuan: Long get() = (budgetYuan - costYuan).coerceAtLeast(0L)
}

object CoveragePlanner {

    const val TICKET_PRICE_YUAN = 2L
    const val MAX_TICKETS = 50
    const val MAX_BUDGET_YUAN = MAX_TICKETS * TICKET_PRICE_YUAN

    /** C(6,4)·C(27,2) + C(6,5)·C(27,1) + C(6,6)，除以 C(33,6)。 */
    private const val RED_FOUR_PLUS_COUNT = 5_428L
    private const val ALL_RED_COMBINATIONS = 1_107_568L
    private val redFourPlusProbability = RED_FOUR_PLUS_COUNT.toDouble() / ALL_RED_COMBINATIONS

    private val secureRandom = SecureRandom()

    /**
     * 生成 [budgetYuan] 元以内的方案；单次最多 [MAX_TICKETS] 注（100 元）。
     *
     * 蓝球先按随机顺序铺满 16 个，再循环；红球按“被使用次数最少优先”分配，
     * 避免几注挤在少数号码上。红球只做覆盖均衡，不改变每个组合的概率。
     */
    fun plan(budgetYuan: Long, random: Random = secureRandom): CoveragePlan {
        val budget = budgetYuan.coerceAtLeast(0L)
        val tickets = (budget / TICKET_PRICE_YUAN)
            .coerceAtMost(MAX_TICKETS.toLong())
            .toInt()
        if (tickets == 0) {
            return CoveragePlan(
                budgetYuan = budget,
                costYuan = 0,
                tickets = emptyList(),
                anyPrizeProbability = 0.0,
                coveredBlues = 0,
            )
        }

        val blues = (1..16).toMutableList().also { it.shuffle(random) }
        val usage = IntArray(34)
        val usedRedSets = HashSet<List<Int>>()
        val result = ArrayList<CoverageTicket>(tickets)

        repeat(tickets) { index ->
            val reds = pickReds(usage, usedRedSets, random)
            reds.forEach { usage[it]++ }
            result += CoverageTicket(reds = reds, blue = blues[index % 16])
        }

        val covered = result.map { it.blue }.toSet().size
        return CoveragePlan(
            budgetYuan = budget,
            costYuan = tickets * TICKET_PRICE_YUAN,
            tickets = result,
            anyPrizeProbability = anyPrizeProbability(tickets, covered),
            coveredBlues = covered,
        )
    }

    /** 至少中一个奖的概率估算；[coveredBlues] 为覆盖到的不同蓝球数。 */
    fun anyPrizeProbability(tickets: Int, coveredBlues: Int): Double {
        if (tickets <= 0 || coveredBlues <= 0) return 0.0
        if (coveredBlues >= 16) return 1.0
        val pBlue = coveredBlues / 16.0
        val pAnyRedFour = 1.0 - (1.0 - redFourPlusProbability).pow(tickets)
        return pBlue + (1.0 - pBlue) * pAnyRedFour
    }

    private fun pickReds(
        usage: IntArray,
        usedRedSets: MutableSet<List<Int>>,
        random: Random,
    ): List<Int> {
        repeat(24) {
            val pool = (1..33).shuffled(random).sortedBy { usage[it] }
            val reds = pool.take(6).sorted()
            if (usedRedSets.add(reds)) return reds
        }
        while (true) {
            val reds = (1..33).shuffled(random).take(6).sorted()
            if (usedRedSets.add(reds)) return reds
        }
    }
}
