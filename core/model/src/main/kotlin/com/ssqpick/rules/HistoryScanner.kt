package com.ssqpick.rules

import com.ssqpick.model.Draw
import com.ssqpick.model.PrizeTier
import com.ssqpick.model.TicketDraft
import kotlin.math.ln
import kotlin.math.pow

/** 历史扫描结果：这张票在历史上最好中过什么。 */
data class HistoryRecord(
    /** 有史以来中过的最高奖级，null 表示从未中过任何奖。 */
    val highestTier: PrizeTier? = null,
    val highestTierDrawCode: String? = null,
    val highestTierAmountYuan: Long = 0L,
    /** 历史最佳红球命中数（0..6）。 */
    val bestRedMatches: Int = 0,
    val blueMatchedAtBest: Boolean = false,
    /** 实际扫描的期数。 */
    val checkedDraws: Int = 0,
    /** 累计中奖金额（元，按各期实付额）。 */
    val totalAmountYuan: Long = 0L,
) {
    val hasEverWon: Boolean get() = highestTier != null
}

object HistoryScanner {

    /**
     * 在 [draws] 中扫描一张票的表现。
     *
     * 使用组合计数而非展开注，因此即使几十万注的复式也能在毫秒内扫完数千年历史。
     */
    fun scan(draft: TicketDraft, draws: List<Draw>): HistoryRecord {
        var bestTier: PrizeTier? = null
        var bestTierCode: String? = null
        var bestTierAmount = 0L
        var bestRed = -1
        var bestRedBlue = false
        var total = 0L

        for (draw in draws) {
            // 两处都复用 TicketRules 的 O(1) 组合计数，不再自己做第二套实现
            val drawBestTier = TicketRules.prizeBreakdown(draft, draw)
                .keys
                .maxByOrNull { it.rank }
            val drawBestRed = TicketRules.redMatchCounts(draft, draw).keys.maxOrNull() ?: -1
            if (drawBestRed > bestRed) {
                bestRed = drawBestRed
                val (blueHit, _) = TicketRules.blueMatchCounts(draft, draw)
                bestRedBlue = blueHit > 0
            }
            if (drawBestTier != null) {
                total += TicketRules.evaluate(draft, draw).first
                if (bestTier == null || drawBestTier.rank > bestTier.rank) {
                    bestTier = drawBestTier
                    bestTierCode = draw.code
                    bestTierAmount = TicketRules.evaluate(draft, draw).first
                }
            }
        }

        return HistoryRecord(
            highestTier = bestTier,
            highestTierDrawCode = bestTierCode,
            highestTierAmountYuan = bestTierAmount,
            bestRedMatches = if (bestRed < 0) 0 else bestRed,
            blueMatchedAtBest = bestRedBlue,
            checkedDraws = draws.size,
            totalAmountYuan = total,
        )
    }
}

/**
 * 一些给 UI 展示用的概率说明。
 *
 * 一注（6+1）在一期里中一等奖的概率 1/17,721,088。因此某个固定组合
 * 在 2073 期历史中一次都没出现过才是正常情况——把这点讲清楚比
 * 编一个"该组合旺/衰"的说法有用。
 */
object OddsInfo {

    const val TOTAL_SPACE = 17_721_088L // C(33,6)*16

    /** 这个组合在历史 n 期里出现过的概率（各期独立）。 */
    fun appearProbability(draws: Int): Double = 1.0 - (1.0 - 1.0 / TOTAL_SPACE).pow(draws)

    fun describe(record: HistoryRecord): String = buildString {
        append("在 ")
        append(record.checkedDraws)
        append(" 期历史中：")
        append(
            if (record.hasEverWon) {
                "最好中过${record.highestTier?.label}"
            } else {
                "未中过任何奖项"
            },
        )
        append("；单注最高命中 ${record.bestRedMatches} 个红球")
        append(if (record.blueMatchedAtBest) " + 蓝球" else "")
        append("。")
    }

    /** 用自然对数表达的"距离感"，供 UI 展示量级。 */
    fun logProbabilityText(): String {
        val logs = ln(TOTAL_SPACE.toDouble())
        return "中一等奖概率 1/${"%,d".format(TOTAL_SPACE)} (≈ e^-%.1f)".format(logs)
    }
}
