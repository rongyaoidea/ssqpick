package com.ssqpick.rules

import com.ssqpick.model.PrizeTier
import com.ssqpick.rules.sim.SimSource
import com.ssqpick.rules.sim.SimSpec
import com.ssqpick.rules.sim.SimStrategy
import com.ssqpick.rules.sim.simulate

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
 * 兼容旧数据页的入口：现在由 [simulate] 的“理论随机 + 每期机选单式”实现。
 *
 * 保留这个函数只是为了不丢历史测试；新代码请直接用 [simulate]，
 * 它能选历史重放、守号、复式和预算。
 */
fun simulateMonteCarlo(paths: Int, drawsPerPath: Int, seed: Long): MonteCarloReport {
    val result = simulate(
        SimSpec(
            source = SimSource.UNIFORM,
            strategy = SimStrategy.RandomSingle,
            paths = paths,
            drawsPerPath = drawsPerPath,
            seed = seed,
        ),
    ) ?: error("模拟被取消")
    return MonteCarloReport(
        paths = paths,
        drawsPerPath = drawsPerPath,
        medianRoi = result.roi.p50,
        profitableShare = result.profitableShare,
        bestRoi = result.roi.max,
        worstRoi = result.roi.min,
        totalCostYuan = result.totalCostYuan,
        totalPrizeYuan = result.totalPrizeYuan,
        tierHits = result.tierStats.mapValues { it.value.hits },
    )
}
