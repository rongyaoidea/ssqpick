package com.ssqpick.rules

import com.ssqpick.model.TicketDraft

/**
 * 把一张票压成位掩码，供模拟/历史扫描的热循环使用。
 *
 * 位序固定：号码 n 对应第 (n-1) 位。红球 1..33、蓝球 1..16 都在 Int 范围内。
 * 这只做数据压缩，不改变任何奖级规则——判定仍走 [TicketRules]。
 */
data class TicketMasks(
    /** 红球池大小（胆码 + 拖码）。 */
    val redPoolSize: Int,
    val redPoolMask: Int,
    val redBoldSize: Int,
    val redBoldMask: Int,
    /** 红拖码数量（不含胆码），胆拖公式要用。 */
    val redPickedSize: Int,
    /** 蓝球注数（拖码数量）。 */
    val blueCount: Int,
    /** 蓝拖码集合；胆码单独放在 [blueBold]。 */
    val blueMask: Int,
    /** 蓝胆码；0 表示没有。 */
    val blueBold: Int,
) {
    companion object {
        fun of(draft: TicketDraft): TicketMasks = TicketMasks(
            redPoolSize = draft.redBold.size + draft.redPicked.size,
            redPoolMask = maskOf(draft.redBold) or maskOf(draft.redPicked),
            redBoldSize = draft.redBold.size,
            redBoldMask = maskOf(draft.redBold),
            redPickedSize = draft.redPicked.size,
            blueCount = draft.bluePicked.size,
            blueMask = maskOf(draft.bluePicked),
            blueBold = draft.blueBold.firstOrNull() ?: 0,
        )

        /** 普通复式（无胆码）的掩码。 */
        fun plain(redCount: Int, redMask: Int, blueCount: Int, blueMask: Int): TicketMasks = TicketMasks(
            redPoolSize = redCount,
            redPoolMask = redMask,
            redBoldSize = 0,
            redBoldMask = 0,
            redPickedSize = redCount,
            blueCount = blueCount,
            blueMask = blueMask,
            blueBold = 0,
        )
    }
}

/** 1-based 号码集合 → 位掩码。 */
fun maskOf(numbers: Iterable<Int>): Int {
    var mask = 0
    for (n in numbers) mask = mask or (1 shl (n - 1))
    return mask
}

/** 草稿 → 掩码的便捷入口。 */
fun TicketDraft.toMasks(): TicketMasks = TicketMasks.of(this)

/** 开奖红球集合 → 位掩码；给热循环直接复用。 */
fun redMaskOf(drawRed: Iterable<Int>): Int = maskOf(drawRed)
