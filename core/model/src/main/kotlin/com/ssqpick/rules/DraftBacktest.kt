package com.ssqpick.rules

import com.ssqpick.model.Draw
import com.ssqpick.model.TicketDraft

/**
 * 一注草稿的历史回测：在本地全部开奖上跑一遍，回答三个问题——
 * 中过几次、蓝中过几次、如果期期都买是赚是赔。
 *
 * 单位是"注·期"（一张复式票一期含多注）：中 k 红的注数逐期用
 * [TicketRules.redMatchCounts] 的 O(1) 组合计数累加，不展开注。
 * 口径与 [HistoryScanner] 同源，同一份数据不会有两张卡打出架来。
 */
data class DraftBacktest(
    val draws: Int,
    val betsPerDraw: Long,
    /** 总注·期 = 注数 × 期数，一切占比的分母。 */
    val totalBetDraws: Long,
    /** 中 k 红 → 注·期（k = 0..6）。 */
    val redHitBets: Map<Int, Long>,
    /** 蓝命中的注·期。 */
    val blueHitBets: Long,
    /** 中过奖的注·期（任意奖级）。 */
    val winningBets: Long,
    /** 假装期期都买的总投入（元）。 */
    val costYuan: Long,
    /** 按各期实付额累加的总奖金（元）。 */
    val prizeYuan: Long,
) {
    fun redRate(k: Int): Double = if (totalBetDraws > 0) {
        (redHitBets[k] ?: 0L).toDouble() / totalBetDraws
    } else {
        0.0
    }

    val blueRate: Double get() = if (totalBetDraws > 0) blueHitBets.toDouble() / totalBetDraws else 0.0

    /** 回报率：奖金 ÷ 投入。长期看它小于 1 是数学，不是运气不好。 */
    val roi: Double get() = if (costYuan > 0) prizeYuan.toDouble() / costYuan else 0.0

    companion object {
        fun empty(draws: Int) = DraftBacktest(
            draws = draws,
            betsPerDraw = 0L,
            totalBetDraws = 0L,
            redHitBets = emptyMap(),
            blueHitBets = 0L,
            winningBets = 0L,
            costYuan = 0L,
            prizeYuan = 0L,
        )
    }
}

fun backtest(draft: TicketDraft, draws: List<Draw>): DraftBacktest {
    if (!TicketRules.validate(draft).isValid) return DraftBacktest.empty(draws.size)
    val bets = TicketRules.betCount(draft)
    val redAcc = HashMap<Int, Long>()
    var blueHit = 0L
    var winning = 0L
    var prize = 0L
    for (d in draws) {
        for ((k, rc) in TicketRules.redMatchCounts(draft, d)) {
            redAcc[k] = (redAcc[k] ?: 0L) + rc
        }
        blueHit += TicketRules.blueMatchCounts(draft, d).first
        // 一次遍历同时拿奖级分布与金额：奖级分布与金额同源，避免 evaluate 再算一遍
        val breakdown = TicketRules.prizeBreakdown(draft, d)
        winning += breakdown.values.sum()
        for ((tier, count) in breakdown) {
            prize += TicketRules.amountFor(tier, d) * count
        }
    }
    return DraftBacktest(
        draws = draws.size,
        betsPerDraw = bets,
        totalBetDraws = bets * draws.size,
        redHitBets = redAcc,
        blueHitBets = blueHit,
        winningBets = winning,
        costYuan = bets * draws.size * TicketRules.PRICE_YUAN,
        prizeYuan = prize,
    )
}

/**
 * 中 k 红的理论概率（单次开奖）。
 *
 * 无胆：超几何分布 Hypergeometric(33, |池|, 6)。
 * 有胆（胆 b 个、拖 t 个）：全中 k = 先中 j 个胆 × 再从拖里中 k-j，
 * 对 j 求和。蓝（单注 1 蓝、无胆）理论命中率恒为 1/16，
 * 蓝胆拖每注 2 蓝则为 2/16——见 [theoreticalBlueHitRate]。
 */
fun theoreticalRedDistribution(draft: TicketDraft): Map<Int, Double> {
    val out = HashMap<Int, Double>()
    val b = draft.redBold.size
    val t = draft.redPicked.size
    for (k in 0..TicketRules.RED_PICK) {
        var p = 0.0
        if (b == 0) {
            p = hypergeometric(population = 33, successes = t, draws = 6, k = k)
        } else {
            for (j in 0..minOf(b, 6)) {
                val pBold = hypergeometric(population = 33, successes = b, draws = 6, k = j)
                if (pBold > 0) {
                    p += pBold * hypergeometric(population = 33 - b, successes = t, draws = 6 - j, k = k - j)
                }
            }
        }
        out[k] = p
    }
    return out
}

/** 每注蓝球命中率的理论值：无胆 1/16，蓝胆拖每注含胆+拖两个蓝 → 2/16。 */
fun theoreticalBlueHitRate(draft: TicketDraft): Double = if (draft.blueBold.isEmpty()) 1.0 / TicketRules.BLUE_TOTAL else 2.0 / TicketRules.BLUE_TOTAL

private fun hypergeometric(population: Int, successes: Int, draws: Int, k: Int): Double {
    if (k < 0 || k > draws || k > successes || draws - k > population - successes) return 0.0
    val total = TicketRules.binom(population.toLong(), draws.toLong()).toDouble()
    if (total <= 0) return 0.0
    return TicketRules.binom(successes.toLong(), k.toLong()) *
        TicketRules.binom((population - successes).toLong(), (draws - k).toLong()) / total
}
