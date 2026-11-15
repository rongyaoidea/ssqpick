package com.ssqpick.rules.strategy

import com.ssqpick.model.Draw
import com.ssqpick.rules.TicketRules
import kotlin.math.pow

/**
 * 策略选号的统计方法。
 *
 * 这些分数只描述历史，不改变开奖独立性。UI 必须显示“不是预测”。
 */
enum class StrategyKind(
    val label: String,
    val shortLabel: String,
    val description: String,
) {
    BAYES(
        label = "贝叶斯后验",
        shortLabel = "贝叶斯",
        description = "Dirichlet 先验平滑近 N 期出现频率，避免小样本把冷号压成 0。",
    ),
    DECAY(
        label = "指数衰减",
        shortLabel = "衰减",
        description = "越近的开奖权重越高，半衰期随窗口长度变化。",
    ),
    COLD(
        label = "遗漏回归",
        shortLabel = "遗漏",
        description = "按“距上次出现”相对窗口期望间隔打分，久未出者分高。",
    ),
    MARKOV(
        label = "马尔可夫转移",
        shortLabel = "马尔可夫",
        description = "以上一期红/蓝为条件，统计下一期的一阶转移概率。",
    ),
    ENSEMBLE(
        label = "综合排序",
        shortLabel = "综合",
        description = "把前四种分数做 Borda 融合，降低单一策略的抽样噪声。",
    ),
}

data class StrategyConfig(
    val kind: StrategyKind = StrategyKind.BAYES,
    val redCount: Int = TicketRules.RED_PICK,
    val blueCount: Int = 1,
    /** null = 使用全部历史；否则只取最近 N 期。 */
    val lookback: Int? = StrategyPicker.DEFAULT_LOOKBACK,
) {
    init {
        require(redCount in TicketRules.RED_PICK..TicketRules.MAX_RED_COMPOUND) { "redCount=$redCount" }
        require(blueCount in 1..TicketRules.BLUE_TOTAL) { "blueCount=$blueCount" }
        require(lookback == null || lookback > 0) { "lookback=$lookback" }
        require(betCount <= TicketRules.EXPAND_LIMIT) { "注数超过上限" }
    }

    val betCount: Long get() = TicketRules.binom(redCount.toLong(), TicketRules.RED_PICK.toLong()) * blueCount
    val lookbackLabel: String get() = lookback?.let { "$it 期" } ?: "全部"
}

data class StrategyPick(
    val kind: StrategyKind,
    val reds: List<Int>,
    val blues: List<Int>,
    val usedDraws: Int,
    val lookback: Int?,
    val redScores: Map<Int, Double>,
    val blueScores: Map<Int, Double>,
    val note: String,
)

object StrategyPicker {

    const val DEFAULT_LOOKBACK = 100

    /** [draws] 必须按时间升序；函数只使用传入的历史，不读未来。 */
    fun recommend(config: StrategyConfig, draws: List<Draw>): StrategyPick {
        require(draws.isNotEmpty()) { "策略选号需要开奖数据" }
        val window = config.lookback?.let { draws.takeLast(it) } ?: draws
        val redScores: Map<Int, Double>
        val blueScores: Map<Int, Double>
        val note: String

        when (config.kind) {
            StrategyKind.BAYES -> {
                redScores = bayesRed(window)
                blueScores = bayesBlue(window)
                note = "Dirichlet(α=1) 平滑；窗口 ${window.size} 期。"
            }

            StrategyKind.DECAY -> {
                redScores = decayRed(window)
                blueScores = decayBlue(window)
                note = "指数衰减，半衰期 ${halfLife(window.size)} 期；窗口 ${window.size} 期。"
            }

            StrategyKind.COLD -> {
                redScores = coldRed(window)
                blueScores = coldBlue(window)
                note = "遗漏相对期望间隔；窗口 ${window.size} 期。"
            }

            StrategyKind.MARKOV -> {
                val red = markovRed(window)
                val blue = markovBlue(window)
                if (red == null || blue == null) {
                    // 只有 1 期时没有转移可数，回落到贝叶斯
                    redScores = bayesRed(window)
                    blueScores = bayesBlue(window)
                    note = "历史不足 2 期，回落到贝叶斯；窗口 ${window.size} 期。"
                } else {
                    redScores = red
                    blueScores = blue
                    note = "以上一期为条件的一阶转移；窗口 ${window.size} 期。"
                }
            }

            StrategyKind.ENSEMBLE -> {
                redScores = ensemble(
                    listOf(bayesRed(window) to 1.0, decayRed(window) to 1.0, coldRed(window) to 0.5, (markovRed(window) ?: bayesRed(window)) to 1.0),
                )
                blueScores = ensemble(
                    listOf(bayesBlue(window) to 1.0, decayBlue(window) to 1.0, coldBlue(window) to 0.5, (markovBlue(window) ?: bayesBlue(window)) to 1.0),
                )
                note = "贝叶斯 / 衰减 / 遗漏 / 马尔可夫 的 Borda 融合；窗口 ${window.size} 期。"
            }
        }

        return StrategyPick(
            kind = config.kind,
            reds = top(redScores, config.redCount),
            blues = top(blueScores, config.blueCount),
            usedDraws = window.size,
            lookback = config.lookback,
            redScores = redScores,
            blueScores = blueScores,
            note = note,
        )
    }

    // ---------------------------------------------------------------- 各策略

    private fun bayesRed(window: List<Draw>): Map<Int, Double> {
        val counts = IntArray(34)
        for (d in window) for (n in d.red) counts[n]++
        val alpha = 1.0
        val denom = window.size + alpha * 33
        return (1..33).associateWith { (counts[it] + alpha) / denom }
    }

    private fun bayesBlue(window: List<Draw>): Map<Int, Double> {
        val counts = IntArray(17)
        for (d in window) counts[d.blue]++
        val alpha = 1.0
        val denom = window.size + alpha * 16
        return (1..16).associateWith { (counts[it] + alpha) / denom }
    }

    private fun decayRed(window: List<Draw>): Map<Int, Double> {
        val scores = DoubleArray(34)
        val hl = halfLife(window.size).toDouble()
        for (age in window.indices) {
            val w = 0.5.pow(age.toDouble() / hl)
            for (n in window[window.lastIndex - age].red) scores[n] += w
        }
        return (1..33).associateWith { scores[it] }
    }

    private fun decayBlue(window: List<Draw>): Map<Int, Double> {
        val scores = DoubleArray(17)
        val hl = halfLife(window.size).toDouble()
        for (age in window.indices) {
            val w = 0.5.pow(age.toDouble() / hl)
            scores[window[window.lastIndex - age].blue] += w
        }
        return (1..16).associateWith { scores[it] }
    }

    private fun coldRed(window: List<Draw>): Map<Int, Double> {
        val last = IntArray(34) { -1 }
        for (i in window.indices) for (n in window[i].red) last[n] = i
        val expected = (window.size / 33.0).coerceAtLeast(1.0)
        return (1..33).associateWith { n ->
            val gap = if (last[n] < 0) window.size else window.size - 1 - last[n]
            gap / expected
        }
    }

    private fun coldBlue(window: List<Draw>): Map<Int, Double> {
        val last = IntArray(17) { -1 }
        for (i in window.indices) last[window[i].blue] = i
        val expected = (window.size / 16.0).coerceAtLeast(1.0)
        return (1..16).associateWith { n ->
            val gap = if (last[n] < 0) window.size else window.size - 1 - last[n]
            gap / expected
        }
    }

    /** 红球一阶转移；不足 2 期返回 null。 */
    private fun markovRed(window: List<Draw>): Map<Int, Double>? {
        if (window.size < 2) return null
        val trans = Array(34) { IntArray(34) }
        val from = IntArray(34)
        for (i in 0 until window.size - 1) {
            val a = window[i].red
            val b = window[i + 1].red
            for (x in a) {
                from[x]++
                for (y in b) trans[x][y]++
            }
        }
        val context = window.last().red
        if (context.isEmpty()) return null
        val beta = 1.0
        return (1..33).associateWith { y ->
            context.sumOf { x ->
                (trans[x][y] + beta) / (from[x] + beta * 33)
            } / context.size
        }
    }

    /** 蓝球一阶转移；不足 2 期返回 null。 */
    private fun markovBlue(window: List<Draw>): Map<Int, Double>? {
        if (window.size < 2) return null
        val trans = Array(17) { IntArray(17) }
        val from = IntArray(17)
        for (i in 0 until window.size - 1) {
            from[window[i].blue]++
            trans[window[i].blue][window[i + 1].blue]++
        }
        val context = window.last().blue
        val beta = 1.0
        return (1..16).associateWith { y ->
            (trans[context][y] + beta) / (from[context] + beta * 16)
        }
    }

    private fun ensemble(parts: List<Pair<Map<Int, Double>, Double>>): Map<Int, Double> {
        val acc = HashMap<Int, Double>()
        var totalWeight = 0.0
        for ((scores, weight) in parts) {
            totalWeight += weight
            val normalized = rankNormalize(scores)
            for ((n, v) in normalized) acc[n] = (acc[n] ?: 0.0) + v * weight
        }
        return acc.mapValues { it.value / totalWeight }
    }

    /** 排名归一化：第一名 1，最后一名 0；同分按号码升序。 */
    private fun rankNormalize(scores: Map<Int, Double>): Map<Int, Double> {
        val order = scores.keys.sortedWith(compareByDescending<Int> { scores[it] ?: 0.0 }.thenBy { it })
        val denom = (order.size - 1).coerceAtLeast(1).toDouble()
        return order.withIndex().associate { (idx, n) -> n to 1.0 - idx / denom }
    }

    private fun top(scores: Map<Int, Double>, count: Int): List<Int> = scores.keys.sortedWith(compareByDescending<Int> { scores[it] ?: 0.0 }.thenBy { it })
        .take(count)
        .sorted()

    private fun halfLife(n: Int): Int = maxOf(5, n / 2)
}
