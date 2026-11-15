package com.ssqpick.rules.sim

import com.ssqpick.model.Draw
import com.ssqpick.model.PrizeTier
import com.ssqpick.model.TicketDraft
import com.ssqpick.rules.TicketMasks
import com.ssqpick.rules.TicketRules
import kotlin.math.roundToInt
import kotlin.random.Random

/** 模拟里一二等奖的估算金额（浮动奖取近年典型量级，仅用于合成开奖）。 */
const val SIM_FIRST_AMOUNT_YUAN = 5_000_000L
const val SIM_SECOND_AMOUNT_YUAN = 150_000L

/** 开奖序列来源：理论均匀随机，或从真实历史里有放回重放。 */
enum class SimSource(val label: String) {
    UNIFORM("理论随机"),
    HISTORICAL("历史重放"),
}

/** 实验的买法。 */
sealed interface SimStrategy {
    /** 每期机选 1 注单式。 */
    data object RandomSingle : SimStrategy

    /** 守一注（可复式/胆拖），每期照买。 */
    data class FixedDraft(val draft: TicketDraft) : SimStrategy

    /** 每期机选一组复式。 */
    data class RandomCompound(val redCount: Int, val blueCount: Int) : SimStrategy
}

/**
 * 一次模拟实验的完整参数。
 *
 * [paths] 是重复次数，[drawsPerPath] 是每条路径连买多少期；两者乘积有上限，
 * 避免在手机上跑出几十秒的 ANR。
 */
data class SimSpec(
    val source: SimSource,
    val strategy: SimStrategy,
    val paths: Int,
    val drawsPerPath: Int,
    /** null = 不设预算；否则是整条路径可花掉的总金额。 */
    val budgetYuan: Long? = null,
    val seed: Long,
) {
    init {
        require(paths in 1..MAX_PATHS) { "paths=$paths 超出 1..$MAX_PATHS" }
        require(drawsPerPath in 1..MAX_DRAWS) { "drawsPerPath=$drawsPerPath 超出 1..$MAX_DRAWS" }
        require(paths.toLong() * drawsPerPath <= maxDrawEvaluations(strategy)) {
            "实验规模过大：$paths×$drawsPerPath 超过 ${maxDrawEvaluations(strategy)} 次开奖判定"
        }
        require(budgetYuan == null || budgetYuan >= 0) { "budgetYuan=$budgetYuan" }
        when (strategy) {
            SimStrategy.RandomSingle -> Unit

            is SimStrategy.FixedDraft -> require(strategy.draft.isValid) { "守号草稿不合法" }

            is SimStrategy.RandomCompound -> {
                require(strategy.redCount in TicketRules.RED_PICK..TicketRules.MAX_RED_COMPOUND)
                require(strategy.blueCount in 1..TicketRules.BLUE_TOTAL)
                require(betsPerDraw <= TicketRules.EXPAND_LIMIT) { "复式注数超过上限" }
            }
        }
    }

    /** 每期买多少注。 */
    val betsPerDraw: Long
        get() = when (strategy) {
            SimStrategy.RandomSingle -> 1L
            is SimStrategy.FixedDraft -> strategy.draft.betCount
            is SimStrategy.RandomCompound -> TicketRules.binom(strategy.redCount.toLong(), 6) * strategy.blueCount
        }

    val costPerDraw: Long get() = betsPerDraw * TicketRules.PRICE_YUAN

    /** 粗略的判定次数，UI 用来提示规模。 */
    val drawEvaluations: Long get() = paths.toLong() * drawsPerPath

    companion object {
        const val MAX_PATHS = 5_000
        const val MAX_DRAWS = 5_000

        /** 单式每期只做一次位运算，允许更大规模。 */
        const val MAX_DRAW_EVALUATIONS = 5_000_000L

        /** 复式/胆拖每期要组装奖级分布，收紧上限避免低端机长时间计算。 */
        const val MAX_DRAFT_DRAW_EVALUATIONS = 500_000L

        fun maxDrawEvaluations(strategy: SimStrategy): Long = when (strategy) {
            SimStrategy.RandomSingle -> MAX_DRAW_EVALUATIONS
            else -> MAX_DRAFT_DRAW_EVALUATIONS
        }
    }
}

/** 分位数集合；p50 即中位数。 */
data class SimQuantiles(
    val min: Double,
    val p5: Double,
    val p25: Double,
    val p50: Double,
    val p75: Double,
    val p95: Double,
    val max: Double,
    val mean: Double,
)

/** fan chart：每个采样点的累计净收益分位线（元）。 */
data class SimFan(
    val xs: IntArray,
    val p5: FloatArray,
    val p25: FloatArray,
    val p50: FloatArray,
    val p75: FloatArray,
    val p95: FloatArray,
)

data class SimTierStat(
    val hits: Long,
    /** 按"注·期 × 单注理论概率"算出的期望命中数。 */
    val expected: Double,
)

data class SimResult(
    val spec: SimSpec,
    val totalCostYuan: Long,
    val totalPrizeYuan: Long,
    val totalBetDraws: Long,
    /** 各路径回报率（奖金/投入）的分布。 */
    val roi: SimQuantiles,
    val profitableShare: Double,
    /** 因预算不足没买满 [SimSpec.drawsPerPath] 的路径占比。 */
    val ruinShare: Double,
    val medianSurvivalDraws: Int,
    /** 单条路径从峰值回落的最大幅度（元）。 */
    val maxDrawdownYuan: SimQuantiles,
    /** 最大回撤 / 该路径实际投入。 */
    val maxDrawdownPct: SimQuantiles,
    val medianLongestNoWin: Int,
    val worstLongestNoWin: Int,
    val tierStats: Map<PrizeTier, SimTierStat>,
    val fan: SimFan,
    val elapsedMs: Long,
) {
    val netYuan: Long get() = totalPrizeYuan - totalCostYuan
}

/**
 * 理论单注概率。
 *
 * 红球命中 k 个服从超几何分布，蓝球独立；所有公式只从
 * [TicketRules.binom] 推导，不另建一套奖级表。
 */
object SimOdds {
    private const val BLUE_HIT = 1.0 / TicketRules.BLUE_TOTAL
    private const val BLUE_MISS = 1.0 - BLUE_HIT

    private val redProb = DoubleArray(TicketRules.RED_PICK + 1) { k ->
        TicketRules.binom(TicketRules.RED_PICK.toLong(), k.toLong()).toDouble() *
            TicketRules.binom(
                (TicketRules.RED_TOTAL - TicketRules.RED_PICK).toLong(),
                (TicketRules.RED_PICK - k).toLong(),
            ) /
            TicketRules.binom(TicketRules.RED_TOTAL.toLong(), TicketRules.RED_PICK.toLong()).toDouble()
    }

    /** 双色球头奖组合空间：C(33,6) × 16。 */
    val firstPrizeSpace: Long =
        TicketRules.binom(TicketRules.RED_TOTAL.toLong(), TicketRules.RED_PICK.toLong()) * TicketRules.BLUE_TOTAL

    fun probability(tier: PrizeTier): Double = when (tier) {
        PrizeTier.FIRST -> redProb[6] * BLUE_HIT
        PrizeTier.SECOND -> redProb[6] * BLUE_MISS
        PrizeTier.THIRD -> redProb[5] * BLUE_HIT
        PrizeTier.FOURTH -> redProb[5] * BLUE_MISS + redProb[4] * BLUE_HIT
        PrizeTier.FIFTH -> redProb[4] * BLUE_MISS + redProb[3] * BLUE_HIT
        PrizeTier.SIXTH -> (redProb[0] + redProb[1] + redProb[2]) * BLUE_HIT
    }

    /** 单注期望命中数 × 注·期。 */
    fun expectedHits(tier: PrizeTier, totalBetDraws: Long): Double = probability(tier) * totalBetDraws
}

/**
 * 跑一次模拟。纯函数、可复现：同一个 [SimSpec.seed] 必然得到同一份结果。
 *
 * 返回 null 表示调用方通过 [shouldCancel] 取消了；UI 应保留上一次结果。
 */
fun simulate(
    spec: SimSpec,
    draws: List<Draw> = emptyList(),
    onProgress: (Float) -> Unit = {},
    shouldCancel: () -> Boolean = { false },
): SimResult? {
    if (spec.source == SimSource.HISTORICAL) {
        require(draws.isNotEmpty()) { "历史重放需要先同步开奖数据" }
    }
    val startNanos = System.nanoTime()
    val rnd = Random(spec.seed)
    val historical = if (spec.source == SimSource.HISTORICAL) draws.map(::indexedDraw) else emptyList()
    val fixedMasks = (spec.strategy as? SimStrategy.FixedDraft)?.draft?.let(TicketMasks::of)

    val paths = spec.paths
    val drawsPerPath = spec.drawsPerPath
    val rois = DoubleArray(paths)
    val nets = DoubleArray(paths)
    val drawCounts = IntArray(paths)
    val maxDrawdowns = DoubleArray(paths)
    val maxDrawdownPcts = DoubleArray(paths)
    val noWinStreaks = IntArray(paths)
    val tierHits = LongArray(PrizeTier.entries.size)
    val tierCounts = LongArray(PrizeTier.entries.size)

    val checkpoints = minOf(96, drawsPerPath + 1)
    val fanXs = IntArray(checkpoints) { j ->
        (j.toLong() * drawsPerPath / (checkpoints - 1).toLong()).toInt()
    }
    val fanValues = Array(checkpoints) { FloatArray(paths) }

    var totalCost = 0L
    var totalPrize = 0L
    var totalBetDraws = 0L
    var ruined = 0
    val progressStep = maxOf(1, paths / 100)

    for (path in 0 until paths) {
        if (shouldCancel()) return null
        var spent = 0L
        var prize = 0L
        var played = 0
        var noWin = 0
        var longestNoWin = 0
        var peak = 0L
        var maxDrawdown = 0L
        var stopped = false
        var nextCheckpoint = 1
        fanValues[0][path] = 0f

        for (i in 0 until drawsPerPath) {
            if (spec.budgetYuan != null && spent + spec.costPerDraw > spec.budgetYuan) {
                stopped = true
                break
            }
            if (i % 256 == 0 && shouldCancel()) return null

            spent += spec.costPerDraw
            val draw = if (historical.isEmpty()) syntheticDraw(rnd) else historical[rnd.nextInt(historical.size)]
            val evaluated = when (val strategy = spec.strategy) {
                SimStrategy.RandomSingle -> evaluateRandomSingle(rnd, draw, tierHits)
                is SimStrategy.FixedDraft -> evaluateMasks(fixedMasks!!, draw, tierCounts, tierHits)
                is SimStrategy.RandomCompound -> evaluateRandomCompound(rnd, strategy, draw, tierCounts, tierHits)
            }
            prize += evaluated.prize
            totalBetDraws += spec.betsPerDraw
            played++
            if (evaluated.won) {
                noWin = 0
            } else {
                noWin++
                if (noWin > longestNoWin) longestNoWin = noWin
            }

            val net = prize - spent
            if (net > peak) peak = net
            val drawdown = peak - net
            if (drawdown > maxDrawdown) maxDrawdown = drawdown

            while (nextCheckpoint < checkpoints && fanXs[nextCheckpoint] <= played) {
                fanValues[nextCheckpoint][path] = net.toFloat()
                nextCheckpoint++
            }
        }

        if (stopped) ruined++
        val net = prize - spent
        rois[path] = if (spent > 0) prize.toDouble() / spent else 0.0
        nets[path] = net.toDouble()
        drawCounts[path] = played
        maxDrawdowns[path] = maxDrawdown.toDouble()
        maxDrawdownPcts[path] = if (spent > 0) maxDrawdown.toDouble() / spent else 0.0
        noWinStreaks[path] = longestNoWin
        while (nextCheckpoint < checkpoints) {
            fanValues[nextCheckpoint][path] = net.toFloat()
            nextCheckpoint++
        }

        totalCost += spent
        totalPrize += prize
        if ((path + 1) % progressStep == 0 || path == paths - 1) {
            onProgress((path + 1).toFloat() / paths)
        }
    }

    val tierStats = PrizeTier.entries.associateWith { tier ->
        SimTierStat(
            hits = tierHits[tier.ordinal],
            expected = SimOdds.expectedHits(tier, totalBetDraws),
        )
    }
    return SimResult(
        spec = spec,
        totalCostYuan = totalCost,
        totalPrizeYuan = totalPrize,
        totalBetDraws = totalBetDraws,
        roi = quantiles(rois),
        profitableShare = nets.count { it > 0.0 }.toDouble() / paths,
        ruinShare = ruined.toDouble() / paths,
        medianSurvivalDraws = median(drawCounts),
        maxDrawdownYuan = quantiles(maxDrawdowns),
        maxDrawdownPct = quantiles(maxDrawdownPcts),
        medianLongestNoWin = median(noWinStreaks),
        worstLongestNoWin = noWinStreaks.maxOrNull() ?: 0,
        tierStats = tierStats,
        fan = fan(fanXs, fanValues),
        elapsedMs = (System.nanoTime() - startNanos) / 1_000_000,
    )
}

// ---------------------------------------------------------------- 内部取样/判定

private class IndexedDraw(
    val redMask: Int,
    val blue: Int,
    val firstAmountYuan: Long,
    val secondAmountYuan: Long,
)

private data class Eval(
    val prize: Long,
    val won: Boolean,
)

private fun indexedDraw(draw: Draw): IndexedDraw = IndexedDraw(
    redMask = draw.red.fold(0) { m, n -> m or (1 shl (n - 1)) },
    blue = draw.blue,
    firstAmountYuan = draw.gradeOf(PrizeTier.FIRST)?.amountPerTicket?.takeIf { it > 0 } ?: SIM_FIRST_AMOUNT_YUAN,
    secondAmountYuan = draw.gradeOf(PrizeTier.SECOND)?.amountPerTicket?.takeIf { it > 0 } ?: SIM_SECOND_AMOUNT_YUAN,
)

private fun syntheticDraw(rnd: Random): IndexedDraw = IndexedDraw(
    redMask = sampleMask(rnd, TicketRules.RED_TOTAL, TicketRules.RED_PICK),
    blue = rnd.nextInt(1, TicketRules.BLUE_TOTAL + 1),
    firstAmountYuan = SIM_FIRST_AMOUNT_YUAN,
    secondAmountYuan = SIM_SECOND_AMOUNT_YUAN,
)

private fun evaluateRandomSingle(rnd: Random, draw: IndexedDraw, tierHits: LongArray): Eval {
    val betRed = sampleMask(rnd, TicketRules.RED_TOTAL, TicketRules.RED_PICK)
    val betBlue = rnd.nextInt(1, TicketRules.BLUE_TOTAL + 1)
    val matches = Integer.bitCount(betRed and draw.redMask)
    val tier = TicketRules.tierFor(matches, betBlue == draw.blue) ?: return Eval(0L, false)
    tierHits[tier.ordinal] += 1
    return Eval(
        prize = TicketRules.amountFor(tier, draw.firstAmountYuan, draw.secondAmountYuan),
        won = true,
    )
}

private fun evaluateMasks(
    masks: TicketMasks,
    draw: IndexedDraw,
    tierCounts: LongArray,
    tierHits: LongArray,
): Eval {
    tierCounts.fill(0L)
    TicketRules.accumulateTierCounts(masks, draw.redMask, draw.blue, tierCounts)
    var prize = 0L
    var won = false
    for (tier in PrizeTier.entries) {
        val count = tierCounts[tier.ordinal]
        if (count > 0) {
            tierHits[tier.ordinal] += count
            prize += TicketRules.amountFor(tier, draw.firstAmountYuan, draw.secondAmountYuan) * count
            won = true
        }
    }
    return Eval(prize, won)
}

private fun evaluateRandomCompound(
    rnd: Random,
    strategy: SimStrategy.RandomCompound,
    draw: IndexedDraw,
    tierCounts: LongArray,
    tierHits: LongArray,
): Eval {
    val masks = TicketMasks.plain(
        redCount = strategy.redCount,
        redMask = sampleMask(rnd, TicketRules.RED_TOTAL, strategy.redCount),
        blueCount = strategy.blueCount,
        blueMask = sampleMask(rnd, TicketRules.BLUE_TOTAL, strategy.blueCount),
    )
    return evaluateMasks(masks, draw, tierCounts, tierHits)
}

/** 从 1..total 中无放回抽 count 个，返回位掩码。 */
private fun sampleMask(rnd: Random, total: Int, count: Int): Int {
    var mask = 0
    var picked = 0
    while (picked < count) {
        val bit = 1 shl rnd.nextInt(total)
        if (mask and bit == 0) {
            mask = mask or bit
            picked++
        }
    }
    return mask
}

// ---------------------------------------------------------------- 统计

private fun quantiles(values: DoubleArray): SimQuantiles {
    val sorted = values.sortedArray()
    fun q(p: Double): Double = if (sorted.isEmpty()) 0.0 else sorted[((sorted.size - 1) * p).roundToInt()]
    return SimQuantiles(
        min = sorted.first(),
        p5 = q(0.05),
        p25 = q(0.25),
        p50 = q(0.50),
        p75 = q(0.75),
        p95 = q(0.95),
        max = sorted.last(),
        mean = sorted.average(),
    )
}

private fun median(values: IntArray): Int {
    if (values.isEmpty()) return 0
    val sorted = values.sortedArray()
    return sorted[sorted.size / 2]
}

private fun fan(xs: IntArray, values: Array<FloatArray>): SimFan {
    val p5 = FloatArray(xs.size)
    val p25 = FloatArray(xs.size)
    val p50 = FloatArray(xs.size)
    val p75 = FloatArray(xs.size)
    val p95 = FloatArray(xs.size)
    for (j in xs.indices) {
        val sorted = values[j].sortedArray()
        p5[j] = sorted[((sorted.size - 1) * 0.05).roundToInt()]
        p25[j] = sorted[((sorted.size - 1) * 0.25).roundToInt()]
        p50[j] = sorted[((sorted.size - 1) * 0.50).roundToInt()]
        p75[j] = sorted[((sorted.size - 1) * 0.75).roundToInt()]
        p95[j] = sorted[((sorted.size - 1) * 0.95).roundToInt()]
    }
    return SimFan(xs, p5, p25, p50, p75, p95)
}
