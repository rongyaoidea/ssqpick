package com.ssqpick.rules

import com.ssqpick.model.PrizeTier
import kotlin.random.Random

/** 模拟里一二等奖的估算金额（浮动奖取近年典型量级，仅用于演示收敛）。 */
const val SIM_FIRST_AMOUNT_YUAN = 5_000_000L
const val SIM_SECOND_AMOUNT_YUAN = 150_000L

data class MonteCarloReport(
    val paths: Int,
    val drawsPerPath: Int,
    /** 中位回报率（奖金/投入）。 */
    val medianRoi: Double,
    /** 盈利路径占比（回报率 > 100%）。 */
    val profitableShare: Double,
    val bestRoi: Double,
    val worstRoi: Double,
    val totalCostYuan: Long,
    val totalPrizeYuan: Long,
    /** 全部路径合计的各奖级命中注数。 */
    val tierHits: Map<PrizeTier, Long>,
)

/**
 * 蒙特卡洛：每期随机买 1 注，连买 [drawsPerPath] 期，重复 [paths] 次。
 *
 * 用固定种子的普通随机数（非密码学）：模拟要可复现，且不需要 CSPRNG。
 * 一个值得说明的等价性：**"每期重新随机"与"守号不换"在分布上完全等价**——
 * 每期是否命中只取决于当期开奖与该注的匹配，与上一期买了什么无关。
 */
fun simulateMonteCarlo(paths: Int, drawsPerPath: Int, seed: Long): MonteCarloReport {
    require(paths > 0 && drawsPerPath > 0) { "paths=$paths drawsPerPath=$drawsPerPath" }
    val rnd = Random(seed)
    val rois = DoubleArray(paths)
    val tierHits = HashMap<PrizeTier, Long>()
    var totalCost = 0L
    var totalPrize = 0L
    for (p in 0 until paths) {
        var prize = 0L
        repeat(drawsPerPath) {
            val drawRed = sampleReds(rnd)
            val drawBlue = rnd.nextInt(1, TicketRules.BLUE_TOTAL + 1)
            val betRed = sampleReds(rnd)
            val betBlue = rnd.nextInt(1, TicketRules.BLUE_TOTAL + 1)
            val matches = betRed.count { it in drawRed }
            val tier = TicketRules.tierFor(matches, betBlue == drawBlue)
            if (tier != null) {
                tierHits[tier] = (tierHits[tier] ?: 0L) + 1L
                prize += when (tier) {
                    PrizeTier.FIRST -> SIM_FIRST_AMOUNT_YUAN
                    PrizeTier.SECOND -> SIM_SECOND_AMOUNT_YUAN
                    else -> tier.fixedAmountYuan
                }
            }
        }
        val cost = drawsPerPath * TicketRules.PRICE_YUAN
        rois[p] = prize.toDouble() / cost
        totalCost += cost
        totalPrize += prize
    }
    val sorted = rois.sortedArray()
    val median = if (sorted.size % 2 == 1) {
        sorted[sorted.size / 2]
    } else {
        (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
    }
    return MonteCarloReport(
        paths = paths,
        drawsPerPath = drawsPerPath,
        medianRoi = median,
        profitableShare = rois.count { it > 1.0 }.toDouble() / paths,
        bestRoi = sorted.last(),
        worstRoi = sorted.first(),
        totalCostYuan = totalCost,
        totalPrizeYuan = totalPrize,
        tierHits = tierHits,
    )
}

/** 部分 Fisher-Yates 取 6 个红球。 */
private fun sampleReds(rnd: Random): IntArray {
    val pool = IntArray(TicketRules.RED_TOTAL) { it + 1 }
    for (i in 0 until TicketRules.RED_PICK) {
        val j = i + rnd.nextInt(TicketRules.RED_TOTAL - i)
        val tmp = pool[i]
        pool[i] = pool[j]
        pool[j] = tmp
    }
    return pool.copyOf(TicketRules.RED_PICK)
}
