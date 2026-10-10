package com.ssqpick.rules

import com.ssqpick.model.Draw
import com.ssqpick.model.TicketDraft

/**
 * 号码冷热统计：出现频次 + 遗漏期数。
 *
 * 输入是本地全部开奖（通常几百期），纯内存计算，毫秒级。
 * **冷热只是历史描述，不预测未来**——UI 上必须配一句说明，
 * 不能让用户误以为热号更容易中。
 */
data class NumberStats(
    val totalDraws: Int,
    val redFrequency: Map<Int, Int>,
    val blueFrequency: Map<Int, Int>,
    /** 遗漏：距今多少期没出。0 表示上期刚出；从未出过记为 [totalDraws]。 */
    val redOmission: Map<Int, Int>,
    val blueOmission: Map<Int, Int>,
) {
    /** 出现最多的 n 个红球（频次并列时号码小的在前，保证稳定）。 */
    fun hotReds(n: Int): List<Int> = top(redFrequency, n)

    /** 遗漏最大的 n 个红球。 */
    fun coldReds(n: Int): List<Int> = top(redOmission, n)

    fun hotBlues(n: Int): List<Int> = top(blueFrequency, n)

    fun coldBlues(n: Int): List<Int> = top(blueOmission, n)

    private fun top(counts: Map<Int, Int>, n: Int): List<Int> = counts.entries
        .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
        .take(n)
        .map { it.key }
}

/**
 * 开奖形态：和值、奇偶、区间、连号。只描述历史分布，
 * 不改变任何号码下期的概率——UI 上必须配这句。
 */
data class ShapeStats(
    val draws: Int,
    val sumMean: Double,
    val sumMin: Int,
    val sumMax: Int,
    /** 5% 分位；90% 的期次和值不低于它。 */
    val sumLow: Int,
    /** 95% 分位；90% 的期次和值不高于它。 */
    val sumHigh: Int,
    /** 平均每期几个奇数。 */
    val oddMean: Double,
    /** 三区（1-11/12-22/23-33）按球次占比。 */
    val zoneShare: List<Double>,
    /** 含连号的期次占比。 */
    val consecutiveShare: Double,
)

fun computeShape(draws: List<Draw>): ShapeStats {
    if (draws.isEmpty()) {
        return ShapeStats(0, 0.0, 0, 0, 0, 0, 0.0, listOf(0.0, 0.0, 0.0), 0.0)
    }
    val sums = draws.map { it.red.sum() }.sorted()
    val oddTotal = draws.sumOf { d -> d.red.count { it % 2 == 1 } }
    val zones = LongArray(3)
    var consecutive = 0
    for (d in draws) {
        for (r in d.red) zones[(r - 1) / 11]++
        if (d.red.sorted().zipWithNext().any { (a, b) -> b - a == 1 }) consecutive++
    }
    val balls = draws.size * 6.0
    return ShapeStats(
        draws = draws.size,
        sumMean = sums.average(),
        sumMin = sums.first(),
        sumMax = sums.last(),
        sumLow = sums[(sums.size * 0.05).toInt().coerceIn(0, sums.size - 1)],
        sumHigh = sums[(sums.size * 0.95).toInt().coerceIn(0, sums.size - 1)],
        oddMean = oddTotal.toDouble() / draws.size,
        zoneShare = zones.map { it / balls },
        consecutiveShare = consecutive.toDouble() / draws.size,
    )
}
fun computeStats(draws: List<Draw>): NumberStats {
    // 按日期升序：遗漏必须从最新一期往回数，顺序错了全错
    val ordered = draws.sortedBy { it.date }
    val redFreq = HashMap<Int, Int>()
    val blueFreq = HashMap<Int, Int>()
    for (d in ordered) {
        for (r in d.red) redFreq[r] = (redFreq[r] ?: 0) + 1
        blueFreq[d.blue] = (blueFreq[d.blue] ?: 0) + 1
    }
    val total = ordered.size
    val redOmission = HashMap<Int, Int>()
    val blueOmission = HashMap<Int, Int>()
    // 从最新一期倒数：第一次见到的距离就是遗漏
    for ((i, d) in ordered.withIndex().reversed()) {
        val gap = total - 1 - i
        for (r in d.red) redOmission.putIfAbsent(r, gap)
        blueOmission.putIfAbsent(d.blue, gap)
    }
    for (r in 1..TicketRules.RED_TOTAL) {
        redFreq.putIfAbsent(r, 0)
        redOmission.putIfAbsent(r, total)
    }
    for (b in 1..TicketRules.BLUE_TOTAL) {
        blueFreq.putIfAbsent(b, 0)
        blueOmission.putIfAbsent(b, total)
    }
    return NumberStats(total, redFreq, blueFreq, redOmission, blueOmission)
}

/**
 * 选号指纹：把当前草稿放进历史分布里定位。
 *
 * 只描述"你这组号在哪、长什么样"，不含任何预测。
 * 和值分位只在恰好 6 红（单式）时有意义——复式/胆拖的和值不唯一。
 */
data class DraftFingerprint(
    val draws: Int,
    val redCount: Int,
    val sum: Int,
    /** 和值在历史分布中的分位（0..1）。 */
    val sumPercentile: Double,
    /** 是否恰好 6 红无胆拖：和的标尺只在此时展示。 */
    val singleShape: Boolean,
    val oddCount: Int,
    val oddMean: Double,
    /** 一/二/三区（1-11/12-22/23-33）各有多少个号码。 */
    val zoneCounts: List<Int>,
    /** 历史三区球次占比。 */
    val zoneShareMean: List<Double>,
)

fun fingerprintOf(draft: TicketDraft, draws: List<Draw>): DraftFingerprint? = fingerprintOf(draft, draws, computeShape(draws))

/**
 * 形态统计由调用方传入（如 ViewModel 按库变化缓存一份）：
 * 指纹随每次选号实时重算，不该顺带重扫一遍全库形态。
 */
fun fingerprintOf(draft: TicketDraft, draws: List<Draw>, shape: ShapeStats): DraftFingerprint? {
    val reds = draft.redPool
    if (reds.isEmpty()) return null
    val sums = draws.map { it.red.sum() }.sorted()
    val sum = reds.sum()
    val below = if (sums.isEmpty()) 0 else sums.count { it < sum }
    return DraftFingerprint(
        draws = draws.size,
        redCount = reds.size,
        sum = sum,
        sumPercentile = if (sums.isEmpty()) 0.0 else below.toDouble() / sums.size,
        singleShape = draft.redBold.isEmpty() && reds.size == TicketRules.RED_PICK,
        oddCount = reds.count { it % 2 == 1 },
        oddMean = shape.oddMean,
        zoneCounts = listOf(
            reds.count { it in 1..11 },
            reds.count { it in 12..22 },
            reds.count { it in 23..33 },
        ),
        zoneShareMean = shape.zoneShare,
    )
}
