package com.ssqpick.rules

import com.ssqpick.model.Bet
import com.ssqpick.model.Draw
import com.ssqpick.model.PrizeTier
import com.ssqpick.model.Ticket
import com.ssqpick.model.TicketDraft
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * 双色球规则的全部领域逻辑：校验、注数、复式展开、对奖。
 *
 * 设计要点：对奖时不展开注（复式可达数十万注），而是用组合计数 O(1) 完成。
 * 一张票在第 k 个红球命中下的注数是可解析求出的：
 *
 * 胆码为空时：注 = P 的全部 6 子集，命中 k 个的注数 = C(|D∩P|, k)·C(|P|-|D∩P|, 6-k)
 * 带胆码 B 时：注 = B ∪ T，T 为拖码的 (6-|B|) 子集，命中 k 个的注数
 *            = C(|T∩D|, k-|B∩D|)·C(|拖|-|T∩D|, (6-|B|)-(k-|B∩D|))
 *
 * 这样在全量历史（数千期）上扫描也是在毫秒级完成。
 */
object TicketRules {

    const val RED_TOTAL = 33
    const val BLUE_TOTAL = 16
    const val RED_PICK = 6
    const val MAX_RED_COMPOUND = 20
    const val PRICE_YUAN = 2L

    /** 超过这个注数就不再展开成列表（防止 UI 卡死），仅用于展示。 */
    const val EXPAND_LIMIT = 50_000L

    enum class ValidationError(val message: String) {
        EMPTY("请选择号码"),
        RED_BOLD_TOO_MANY("红球胆码最多 5 个"),
        RED_BOLD_OVERLAP("胆码不能与拖码重复"),
        RED_NOT_ENOUGH("红球数量不足"),
        RED_TOO_MANY("红球复式最多 20 个"),
        RED_POOL_OVERFLOW("红球号码超出 33 个"),
        BLUE_BOLD_TOO_MANY("蓝球胆码最多 1 个"),
        BLUE_BOLD_OVERLAP("蓝球胆码不能与拖码重复"),
        BLUE_NOT_ENOUGH("至少选择 1 个蓝球"),
        BLUE_POOL_OVERFLOW("蓝球号码超出 16 个"),
        TOO_MANY_BETS("注数过多"),
    }

    data class Validation(val isValid: Boolean, val error: ValidationError?, val betCount: Long) {
        val message: String? get() = error?.message
    }

    fun validate(draft: TicketDraft): Validation {
        val betCount = betCountOrZero(draft)
        val error = firstError(draft) ?: run {
            if (betCount > EXPAND_LIMIT) ValidationError.TOO_MANY_BETS else null
        }
        return Validation(error == null, error, betCount)
    }

    private fun firstError(draft: TicketDraft): ValidationError? {
        if (draft.isEmpty) return ValidationError.EMPTY
        if (draft.redBold.size > 5) return ValidationError.RED_BOLD_TOO_MANY
        if (draft.redBold.any { it in draft.redPicked }) return ValidationError.RED_BOLD_OVERLAP
        if (draft.redPool.size > RED_TOTAL) return ValidationError.RED_POOL_OVERFLOW
        if (draft.blueBold.size > 1) return ValidationError.BLUE_BOLD_TOO_MANY
        if (draft.blueBold.any { it in draft.bluePicked }) return ValidationError.BLUE_BOLD_OVERLAP
        if (draft.bluePool.isEmpty() || draft.bluePicked.isEmpty()) return ValidationError.BLUE_NOT_ENOUGH
        if (draft.bluePool.size > BLUE_TOTAL) return ValidationError.BLUE_POOL_OVERFLOW

        val needRed = RED_PICK - draft.redBold.size
        if (draft.redPicked.size < needRed) return ValidationError.RED_NOT_ENOUGH
        if (draft.redBold.isEmpty() && draft.redPicked.size > MAX_RED_COMPOUND) {
            return ValidationError.RED_TOO_MANY
        }
        return null
    }

    fun betCount(draft: TicketDraft): Long {
        val err = firstError(draft)
        if (err != null) return 0L
        return redCombos(draft) * blueCombos(draft)
    }

    private fun betCountOrZero(draft: TicketDraft): Long = if (firstError(draft) != null) 0L else redCombos(draft) * blueCombos(draft)

    /** 红球组合数。 */
    fun redCombos(draft: TicketDraft): Long {
        val b = draft.redBold.size
        val picked = draft.redPicked.size
        return if (b == 0) {
            if (picked == RED_PICK) 1L else binom(picked.toLong(), RED_PICK.toLong())
        } else {
            binom(picked.toLong(), (RED_PICK - b).toLong())
        }
    }

    /** 蓝球组合数：每个拖码配一注。 */
    fun blueCombos(draft: TicketDraft): Long = draft.bluePicked.size.toLong()

    // ---------------------------------------------------------------- 展开

    /**
     * 展开成注列表。注数超过 [EXPAND_LIMIT] 时只返回前 [EXPAND_LIMIT] 注，
     * 调用方应据此提示而不要依赖 list.size 当作总注数。
     */
    fun expand(draft: TicketDraft, limit: Long = EXPAND_LIMIT): List<Bet> {
        if (firstError(draft) != null) return emptyList()
        val reds = redSequences(draft)
        val blues = blueSequences(draft)
        val out = ArrayList<Bet>()
        outer@ for (r in reds) {
            for (b in blues) {
                out += Bet(r, b)
                if (out.size.toLong() >= limit) break@outer
            }
        }
        return out
    }

    private fun redSequences(draft: TicketDraft): Sequence<List<Int>> {
        val bold = draft.redBold.sorted()
        val picked = draft.redPicked.sorted()
        return if (bold.isEmpty()) {
            combinations(picked, RED_PICK)
        } else {
            val need = RED_PICK - bold.size
            combinations(picked, need).map { it + bold }
        }
    }

    /**
     * 每注的蓝球号码集合。
     * 胆拖时每个拖码配上一组 [胆码, 拖码]，任一个对上开奖蓝球都算中。
     */
    private fun blueSequences(draft: TicketDraft): Sequence<List<Int>> {
        val picked = draft.bluePicked.sorted()
        val bold = draft.blueBold.sorted()
        return if (bold.isEmpty()) {
            picked.asSequence().map { listOf(it) }
        } else {
            picked.asSequence().map { bold + it }
        }
    }

    /** 生成 arr 的全部 k 子组合（升序，字典序）。 */
    fun combinations(arr: List<Int>, k: Int): Sequence<List<Int>> = sequence {
        if (k < 0 || k > arr.size) return@sequence
        if (k == 0) {
            yield(emptyList())
            return@sequence
        }
        val idx = IntArray(k) { it }
        while (true) {
            yield(List(k) { arr[idx[it]] })
            var i = k - 1
            while (i >= 0 && idx[i] == arr.size - k + i) i--
            if (i < 0) return@sequence
            idx[i]++
            for (j in i + 1 until k) idx[j] = idx[j - 1] + 1
        }
    }

    // ---------------------------------------------------------------- 对奖

    /**
     * 一注（6 红 + 1 蓝）对应的奖级。与官方奖级表逐条对应。
     */
    fun tierFor(redMatches: Int, blueMatched: Boolean): PrizeTier? = when {
        redMatches == 6 && blueMatched -> PrizeTier.FIRST
        redMatches == 6 -> PrizeTier.SECOND
        redMatches == 5 && blueMatched -> PrizeTier.THIRD
        redMatches == 5 -> PrizeTier.FOURTH
        redMatches == 4 && blueMatched -> PrizeTier.FOURTH
        redMatches == 4 -> PrizeTier.FIFTH
        redMatches == 3 && blueMatched -> PrizeTier.FIFTH
        blueMatched -> PrizeTier.SIXTH
        else -> null
    }

    fun countRedMatches(betRed: List<Int>, drawRed: List<Int>): Int = betRed.count { it in drawRed }

    /** 单注命中的奖级。 */
    fun resultFor(bet: Bet, draw: Draw): Pair<PrizeTier?, Int> {
        val rm = countRedMatches(bet.red, draw.red)
        val bm = draw.blue in bet.blues
        return tierFor(rm, bm) to rm
    }

    /**
     * 一张票在某一期的奖级 → 中奖注数分布。O(1) 于注数。
     */
    fun prizeBreakdown(draft: TicketDraft, draw: Draw): Map<PrizeTier, Long> {
        if (firstError(draft) != null) return emptyMap()
        return prizeBreakdown(draft.toMasks(), redMaskOf(draw.red), draw.blue)
    }

    /**
     * 位掩码版本：给模拟器每期调用，避免反复构造 [Draw] 与 Set。
     * 调用方负责保证掩码来自合法票面（[TicketMasks.of] / [TicketMasks.plain]）。
     */
    fun prizeBreakdown(masks: TicketMasks, drawRedMask: Int, drawBlue: Int): Map<PrizeTier, Long> {
        val (blueHit, blueTotal) = blueMatchCounts(masks, drawBlue)
        return prizeBreakdownOf(redMatchCounts(masks, drawRedMask), blueHit, blueTotal)
    }

    /**
     * 由已算出的红球命中分布与蓝球命中数组装奖级 → 注数。
     *
     * 调用方手里已有 [redMatchCounts]/[blueMatchCounts] 结果时（如历史扫描
     * 逐期只算一次），用这个避免重复计算。
     */
    fun prizeBreakdownOf(redCounts: Map<Int, Long>, blueHit: Long, blueTotal: Long): Map<PrizeTier, Long> {
        val blueMiss = blueTotal - blueHit
        if (blueHit < 0 || blueMiss < 0) return emptyMap()

        val acc = HashMap<PrizeTier, Long>()
        for ((k, rc) in redCounts) {
            if (rc <= 0L) continue
            tierFor(k, true)?.let { acc[it] = (acc[it] ?: 0L) + rc * blueHit }
            tierFor(k, false)?.let { acc[it] = (acc[it] ?: 0L) + rc * blueMiss }
        }
        return acc.filterValues { it > 0L }
    }

    /** 红球命中数 → 注数。 */
    fun redMatchCounts(draft: TicketDraft, draw: Draw): Map<Int, Long> = redMatchCounts(draft.toMasks(), redMaskOf(draw.red))

    /** 位掩码版本：只依赖红球命中位，给模拟热循环复用。 */
    fun redMatchCounts(masks: TicketMasks, drawRedMask: Int): Map<Int, Long> {
        val out = HashMap<Int, Long>()
        forEachRedCount(masks, drawRedMask) { k, count ->
            if (count > 0) out[k] = count
        }
        return out
    }

    /**
     * 把一张票在本期的各奖级注数**累加**到 [out]（调用方负责清零）。
     *
     * 模拟器每期都要算一次奖级，返回 Map 会产生大量短命对象；这个入口
     * 让调用方复用一个 LongArray，口径仍与 [prizeBreakdownOf] 完全一致。
     */
    fun accumulateTierCounts(masks: TicketMasks, drawRedMask: Int, drawBlue: Int, out: LongArray) {
        require(out.size >= PrizeTier.entries.size) { "out 至少需要 ${PrizeTier.entries.size} 个槽位" }
        val (blueHit, blueTotal) = blueMatchCounts(masks, drawBlue)
        val blueMiss = blueTotal - blueHit
        if (blueHit < 0 || blueMiss < 0) return
        forEachRedCount(masks, drawRedMask) { k, count ->
            if (count <= 0) return@forEachRedCount
            tierFor(k, true)?.let { out[it.ordinal] += count * blueHit }
            tierFor(k, false)?.let { out[it.ordinal] += count * blueMiss }
        }
    }

    /** 遍历"命中 k 个红球的注数"，不构造 Map。 */
    private inline fun forEachRedCount(
        masks: TicketMasks,
        drawRedMask: Int,
        action: (k: Int, count: Long) -> Unit,
    ) {
        val dP = Integer.bitCount(masks.redPoolMask and drawRedMask).toLong()
        if (masks.redBoldSize == 0) {
            val n = masks.redPoolSize.toLong()
            for (k in 0..RED_PICK) {
                action(k, binom(dP, k.toLong()) * binom(n - dP, (RED_PICK - k).toLong()))
            }
            return
        }

        val dB = Integer.bitCount(masks.redBoldMask and drawRedMask).toLong()
        val dT = dP - dB
        val b = masks.redBoldSize.toLong()
        val picked = masks.redPickedSize.toLong()
        for (k in 0..RED_PICK) {
            val j = k - dB
            if (j < 0 || j > RED_PICK - b || (RED_PICK - b) - j < 0 ||
                (RED_PICK - b) - j > picked - dT
            ) {
                continue
            }
            action(k, binom(dT, j) * binom(picked - dT, (RED_PICK - b) - j))
        }
    }

    /** 蓝球：命中注数 与 总注数。 */
    fun blueMatchCounts(draft: TicketDraft, draw: Draw): Pair<Long, Long> = blueMatchCounts(draft.toMasks(), draw.blue)

    /** 位掩码版本：只依赖开奖蓝球。 */
    fun blueMatchCounts(masks: TicketMasks, drawBlue: Int): Pair<Long, Long> {
        val total = masks.blueCount.toLong()
        val drawBit = 1 shl (drawBlue - 1)
        if (masks.blueBold == 0) {
            val hit = if (masks.blueMask and drawBit != 0) 1L else 0L
            return hit to total
        }
        val hit = when {
            masks.blueBold == drawBlue -> total
            masks.blueMask and drawBit != 0 -> 1L
            else -> 0L
        }
        return hit to total
    }

    /** 单注奖金（元）。浮动奖取开奖公告实付额，缺失时回落到固定奖。 */
    fun amountFor(tier: PrizeTier, draw: Draw): Long = amountFor(
        tier = tier,
        firstAmountYuan = draw.gradeOf(PrizeTier.FIRST)?.amountPerTicket ?: 0L,
        secondAmountYuan = draw.gradeOf(PrizeTier.SECOND)?.amountPerTicket ?: 0L,
    )

    /** 不构造 [Draw] 的奖金口径，给模拟器的合成开奖使用。 */
    fun amountFor(tier: PrizeTier, firstAmountYuan: Long, secondAmountYuan: Long): Long = when (tier) {
        PrizeTier.FIRST -> firstAmountYuan
        PrizeTier.SECOND -> secondAmountYuan
        else -> tier.fixedAmountYuan
    }

    /** 一张票对某一期的总奖金（元）与各奖级注数。 */
    fun evaluate(draft: TicketDraft, draw: Draw): Pair<Long, Map<PrizeTier, Long>> {
        val breakdown = prizeBreakdown(draft, draw)
        var total = 0L
        for ((tier, count) in breakdown) total += amountFor(tier, draw) * count
        return total to breakdown
    }

    // ---------------------------------------------------------------- 工具

    /** C(n,k)，越界返回 0。 */
    fun binom(n: Long, k: Long): Long {
        if (k < 0 || n < 0 || k > n) return 0L
        if (k > n - k) return binom(n, n - k)
        var r = 1L
        for (i in 0 until k) {
            r = r * (n - i) / (i + 1)
        }
        return r
    }

    // ---------------------------------------------------------------- 存票状态

    /**
     * 一张存票相对**它自己那一期**的状态。
     *
     * 每张票绑定"保存时刻之后的第一期开奖"（[bindTicketDraw]）：本地还没有
     * 这一期就是未开奖；开过就按那一期实算。不能用"最新一期"去判——
     * 存给下一期的票，拿上一期的结果去对没有意义。
     */
    fun statusOf(ticket: Ticket, drawsAsc: List<Draw>): TicketStatus {
        val bound = bindTicketDraw(ticket, drawsAsc) ?: return TicketStatus.NotDrawn
        val (amount, breakdown) = evaluate(ticket.draft, bound)
        // 按"有没有奖级"而不是"有没有金额"判定：一二等奖是浮动奖，
        // 没有公告实付额时金额为 0，但奖级本身是中了的
        return if (breakdown.isNotEmpty()) {
            TicketStatus.Won(amount, breakdown.keys.maxByOrNull { it.rank })
        } else {
            TicketStatus.Missed
        }
    }
}

/** 存票相对最新一期的状态：未开奖 / 未中奖 / 中奖金额。 */
sealed interface TicketStatus {
    data object NotDrawn : TicketStatus
    data object Missed : TicketStatus
    data class Won(val amountYuan: Long, val topTier: PrizeTier?) : TicketStatus
}
