package com.ssqpick.rules

import com.ssqpick.model.Draw
import com.ssqpick.model.PrizeTier
import com.ssqpick.model.TicketDraft

/**
 * 摇号前的“排除历史中奖组合”。
 *
 * 语义不是“去掉中过奖的单个号码”——每个红球都在历史一等奖里出现过，
 * 那样会把球池清空。这里排除的是**整张号码**：如果摇出的红蓝组合
 * 在历史开奖中曾经达到所选奖级，就重新摇。
 *
 * 判定用组合计数而不是展开注（复式 20 红 16 蓝有数十万注），
 * 一三等奖还可以用位掩码直接判，2000 期 × 2000 次尝试也只是千万级整数运算。
 *
 * 过滤器不改变“每注等概率”：它只是把样本空间条件到“没有中过所选奖级”
 * 的子集里，子集内部仍然均匀。
 */
object HistoryFilter {

    /**
     * 拒样上限：保证 UI 最长等待有界。小复式通常一次通过；
     * 大复式再勾三等奖可能本来就无解，达到上限返回 null 由 UI 明示。
     */
    const val MAX_ATTEMPTS = 2_000

    data class Sample(
        /** 红球（升序）。 */
        val reds: List<Int>,
        /** 蓝球（升序）。 */
        val blues: List<Int>,
        /** 实际尝试次数，供 UI 展示“筛了几轮”。 */
        val attempts: Int,
    )

    /**
     * 从 1..33 / 1..16 里反复取样，直到号码在 [draws] 中**没有**中过 [tiers] 里的
     * 任何一个奖级。达到 [maxAttempts] 仍无解时返回 null——调用方应提示用户
     * 减少排除项或复式规模，而不是偷偷忽略过滤器。
     */
    fun sample(
        redCount: Int,
        blueCount: Int,
        draws: List<Draw>,
        tiers: Set<PrizeTier>,
        maxAttempts: Int = MAX_ATTEMPTS,
    ): Sample? = sample(
        redCount = redCount,
        blueCount = blueCount,
        draws = draws,
        tiers = tiers,
        maxAttempts = maxAttempts,
        nextNumbers = {
            NumberGenerator.sample(TicketRules.RED_TOTAL, redCount) to
                NumberGenerator.sample(TicketRules.BLUE_TOTAL, blueCount)
        },
    )

    /** 测试用入口：注入确定性的取样器，避免概率型断言。 */
    internal fun sample(
        redCount: Int,
        blueCount: Int,
        draws: List<Draw>,
        tiers: Set<PrizeTier>,
        maxAttempts: Int,
        nextNumbers: () -> Pair<List<Int>, List<Int>>,
    ): Sample? {
        if (tiers.isEmpty() || draws.isEmpty()) return null
        if (redCount !in 1..TicketRules.RED_TOTAL || blueCount !in 1..TicketRules.BLUE_TOTAL) return null

        val index = draws.map(IndexedDraw::of)
        repeat(maxAttempts.coerceAtLeast(1)) { attempt ->
            val (reds, blues) = nextNumbers()
            if (reds.size != redCount || blues.size != blueCount) return null
            if (!hitsAny(reds, blues, index, tiers)) {
                return Sample(reds = reds, blues = blues, attempts = attempt + 1)
            }
        }
        return null
    }

    /** 这张票在全部历史里有没有中过 [tiers] 中的任意一档。 */
    fun hitsAny(draft: TicketDraft, draws: List<Draw>, tiers: Set<PrizeTier>): Boolean {
        if (tiers.isEmpty() || draws.isEmpty()) return false
        return draws.any { draw -> tiers.any { tier -> hits(draft, draw, tier) } }
    }

    /**
     * 单期判定：这张票在该期是否**至少有一注**达到 [tier]。
     *
     * 与 [TicketRules.prizeBreakdown] 的口径一致；无胆拖的普通复式走
     * 位运算快路径，胆拖才回落到组合计数。
     */
    fun hits(draft: TicketDraft, draw: Draw, tier: PrizeTier): Boolean {
        if (draft.isEmpty || draw.red.size != TicketRules.RED_PICK) return false
        if (draft.redBold.isEmpty() && draft.blueBold.isEmpty()) {
            return when (tier) {
                PrizeTier.FIRST, PrizeTier.SECOND, PrizeTier.THIRD -> hitsPlain(
                    reds = draft.redPicked,
                    blues = draft.bluePicked,
                    draw = draw,
                    tier = tier,
                )

                // 四~六等奖不提供 UI 选项；保留通用回落，便于将来扩展。
                else -> TicketRules.prizeBreakdown(draft, draw).containsKey(tier)
            }
        }
        return TicketRules.prizeBreakdown(draft, draw).containsKey(tier)
    }

    private fun hitsPlain(reds: Set<Int>, blues: Set<Int>, draw: Draw, tier: PrizeTier): Boolean {
        val redHits = draw.red.count { it in reds }
        val blueHit = draw.blue in blues
        val hasOtherRed = reds.size > redHits
        // 蓝球集合里是否存在“不等于开奖蓝球”的号码
        val hasOtherBlue = blues.size > if (blueHit) 1 else 0
        return when (tier) {
            // 一等奖：6 红全中 + 开奖蓝球在票内
            PrizeTier.FIRST -> redHits == TicketRules.RED_PICK && blueHit

            // 二等奖：6 红全中 + 至少一注押了别的蓝球
            PrizeTier.SECOND -> redHits == TicketRules.RED_PICK && hasOtherBlue

            // 三等奖：恰好 5 红 + 开奖蓝球。复式里要留一个非开奖红球给第 6 个位置，
            // 否则 6 红全中时对应的是前两档，不是三等奖。
            PrizeTier.THIRD -> redHits >= TicketRules.RED_PICK - 1 && hasOtherRed && blueHit

            else -> false
        }
    }

    private fun hitsAny(
        reds: List<Int>,
        blues: List<Int>,
        draws: List<IndexedDraw>,
        tiers: Set<PrizeTier>,
    ): Boolean {
        // UI 只提供前三档；出现更低奖级时走通用组合计数，保证语义不漂移。
        if (tiers.any { it.ordinal > PrizeTier.THIRD.ordinal }) {
            val draft = TicketDraft(redPicked = reds.toSet(), bluePicked = blues.toSet())
            return draws.any { draw -> tiers.any { hits(draft, draw.toDraw(), it) } }
        }

        var redMask = 0
        for (n in reds) redMask = redMask or (1 shl (n - 1))
        var blueMask = 0
        for (n in blues) blueMask = blueMask or (1 shl (n - 1))
        val redCount = reds.size
        val blueCount = blues.size

        for (draw in draws) {
            val redHits = Integer.bitCount(redMask and draw.redMask)
            val blueHit = blueMask and (1 shl (draw.blue - 1)) != 0
            val hasOtherRed = redCount > redHits
            val hasOtherBlue = blueCount > if (blueHit) 1 else 0
            for (tier in tiers) {
                val hit = when (tier) {
                    PrizeTier.FIRST -> redHits == TicketRules.RED_PICK && blueHit
                    PrizeTier.SECOND -> redHits == TicketRules.RED_PICK && hasOtherBlue
                    PrizeTier.THIRD -> redHits >= TicketRules.RED_PICK - 1 && hasOtherRed && blueHit
                    else -> false
                }
                if (hit) return true
            }
        }
        return false
    }

    /** 预先把开奖红球压成位掩码，避免每次尝试都重新 count/contains。 */
    private class IndexedDraw(val redMask: Int, val blue: Int, val source: Draw) {
        fun toDraw(): Draw = source

        companion object {
            fun of(draw: Draw): IndexedDraw {
                var mask = 0
                for (n in draw.red) mask = mask or (1 shl (n - 1))
                return IndexedDraw(redMask = mask, blue = draw.blue, source = draw)
            }
        }
    }
}
