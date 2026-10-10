package com.ssqpick.rules

import com.ssqpick.model.TicketDraft

/**
 * 上海福彩短信投注指令（00CP 体系，双色球彩种代码 01）。
 *
 * 现行格式（2026-10 用户提供并核对）：
 * - 单式/复式：`00CP#01#红球*蓝球#倍数`（复式条件：红≥7 或 蓝≥2 或同时满足）
 * - 红胆拖：`00CP#01#胆码*拖码*蓝球#倍数`（蓝球可多个，连写；不支持蓝胆拖——双色球没有该玩法）
 * - 单式多注：`00CP#01#红*蓝**红*蓝#倍数`（`**` 分隔，最多 5 注、仅单式）
 * - 多期：倍数后再加 `#期数`；默认不带（单期）
 *
 * 号码一律两位连写（如 01、33）。不支持或非法的输入返回 null，由 UI 决定如何提示。
 */
object SmsCommand {

    /** 双色球的彩种代码。 */
    const val SSQ = "01"

    /** 一条短信最多包含的单式注数。 */
    const val MAX_MULTI_BETS = 5

    /** 倍数上限（双色球多倍投注 2~99 倍，1 即单倍）。 */
    const val MAX_TIMES = 99

    /** 期数上限：界面先给到 5（官方示例为 5 期），有新的官方说明再调。 */
    const val MAX_PERIODS = 5

    /** 一张票的投注指令；不支持（蓝胆拖）或非法返回 null。 */
    fun of(draft: TicketDraft, times: Int = 1, periods: Int = 1): String? {
        if (!TicketRules.validate(draft).isValid) return null
        // 蓝胆拖不是双色球官方玩法，没有对应指令写法，不猜
        if (draft.blueBold.isNotEmpty()) return null
        val body = if (draft.redBold.isNotEmpty()) {
            // 红胆拖：胆*拖*蓝（蓝可多个，连写）
            concat(draft.redBold) + "*" + concat(draft.redPicked) + "*" + concat(draft.bluePool)
        } else {
            // 单式与复式同一形状：红*蓝
            concat(draft.redPicked) + "*" + concat(draft.bluePool)
        }
        return "00CP#$SSQ#$body" + tail(times, periods)
    }

    /** 单式多注：一条短信 1~5 注单式，`**` 分隔。 */
    fun multi(drafts: List<TicketDraft>, times: Int = 1, periods: Int = 1): String? {
        if (drafts.isEmpty() || drafts.size > MAX_MULTI_BETS) return null
        val bodies = ArrayList<String>(drafts.size)
        for (d in drafts) {
            val isSimple = d.redBold.isEmpty() && d.blueBold.isEmpty() &&
                d.redPicked.size == TicketRules.RED_PICK && d.bluePicked.size == 1
            if (!isSimple || !TicketRules.validate(d).isValid) return null
            bodies += concat(d.redPicked) + "*" + concat(d.bluePicked)
        }
        return "00CP#$SSQ#" + bodies.joinToString("**") + tail(times, periods)
    }

    private fun concat(numbers: Set<Int>): String = numbers.sorted().joinToString("") { "%02d".format(it) }

    private fun tail(times: Int, periods: Int): String {
        val t = times.coerceIn(1, MAX_TIMES)
        val p = periods.coerceIn(1, MAX_PERIODS)
        return if (p > 1) "#$t#$p" else "#$t"
    }
}
