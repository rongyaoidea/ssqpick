package com.ssqpick.model

import com.ssqpick.rules.TicketRules
import kotlinx.serialization.Serializable

/** 票种。 */
enum class TicketKind(val label: String) {
    SIMPLE("单式"),
    RED_COMPOUND("红复式"),
    BLUE_COMPOUND("蓝复式"),
    FULL_COMPOUND("双区复式"),
    RED_DAN_TUO("红胆拖"),
    BLUE_DAN_TUO("蓝胆拖"),
    ;

    val isCompound: Boolean get() = this != SIMPLE
}

/**
 * 未保存的号码选择草稿。胆码与拖码分开存放，二者互不重叠（标准胆拖表示法）。
 *
 * - 红球：胆码 0~5 个，其余从拖码中补齐到 6 个。胆码为空时直接选 6 个（单式）
 *   或 7~20 个（红复式）。
 * - 蓝球：胆码 0~1 个，拖码 1~16 个（不含胆码）。
 */
@Serializable
data class TicketDraft(
    val redBold: Set<Int> = emptySet(),
    val redPicked: Set<Int> = emptySet(),
    val blueBold: Set<Int> = emptySet(),
    val bluePicked: Set<Int> = emptySet(),
) {
    val redPool: Set<Int> get() = redBold + redPicked
    val bluePool: Set<Int> get() = blueBold + bluePicked

    /** 是否已构成合法票。 */
    val isValid: Boolean get() = TicketRules.validate(this).isValid
    val kind: TicketKind
        get() = when {
            redBold.isEmpty() && redPicked.size == 6 && bluePicked.size == 1 && blueBold.isEmpty() -> TicketKind.SIMPLE
            redBold.isNotEmpty() && blueBold.isEmpty() -> TicketKind.RED_DAN_TUO
            blueBold.isNotEmpty() && redBold.isEmpty() -> TicketKind.BLUE_DAN_TUO
            redPicked.size > 6 && bluePicked.size > 1 -> TicketKind.FULL_COMPOUND
            redPicked.size > 6 -> TicketKind.RED_COMPOUND
            else -> TicketKind.BLUE_COMPOUND
        }

    /** 总注数与单注价格无关，仅返回注数。 */
    val betCount: Long get() = TicketRules.betCount(this)

    val priceYuan: Long get() = betCount * 2L

    /** 草稿是否为空。 */
    val isEmpty: Boolean get() = redPool.isEmpty() && bluePool.isEmpty()

    fun withRed(number: Int): TicketDraft = copy(redPicked = redPicked + number)

    fun withoutRed(number: Int): TicketDraft = copy(redPicked = redPicked - number, redBold = redBold - number)

    fun withBlue(number: Int): TicketDraft = copy(bluePicked = bluePicked + number)

    fun withoutBlue(number: Int): TicketDraft = copy(bluePicked = bluePicked - number, blueBold = blueBold - number)
}

/** 已保存的票。 */
@Serializable
data class Ticket(
    val id: Long = 0L,
    val createdAtEpochMs: Long,
    val label: String? = null,
    val draft: TicketDraft,
) {
    val betCount: Long get() = draft.betCount
    val priceYuan: Long get() = draft.priceYuan
    val kind: TicketKind get() = draft.kind
}

/** 一注命中的结果。 */
data class BetResult(
    val bet: Bet,
    val redMatches: Int,
    val blueMatched: Boolean,
    val tier: PrizeTier?,
)

/** 一张票在某一期的对奖结果。 */
data class TicketCheckResult(
    val ticketId: Long,
    val drawCode: String,
    val totalAmountYuan: Long,
    val winningBetCount: Long,
    val tierCounts: Map<PrizeTier, Long>,
    /** 各奖级单注奖金（一二等奖取开奖公告的实付额）。 */
    val tierAmounts: Map<PrizeTier, Long>,
) {
    val topTier: PrizeTier? get() = tierCounts.keys.maxByOrNull { it.rank }

    /** 按奖级判定：一二等奖是浮动奖，公告金额缺失时金额为 0，但奖级本身中了。 */
    val hasWon: Boolean get() = tierCounts.isNotEmpty()
}
