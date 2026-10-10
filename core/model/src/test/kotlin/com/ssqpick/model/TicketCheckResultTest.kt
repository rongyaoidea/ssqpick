package com.ssqpick.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 中奖判定口径：按奖级而非金额。
 * 一二等奖是浮动奖，开奖公告实付额缺失时金额为 0，但奖级本身是中了。
 */
class TicketCheckResultTest {

    private fun result(total: Long, tiers: Map<PrizeTier, Long>) = TicketCheckResult(
        ticketId = 1L,
        drawCode = "2026001",
        totalAmountYuan = total,
        winningBetCount = tiers.values.sum(),
        tierCounts = tiers,
        tierAmounts = tiers.keys.associateWith { 0L },
    )

    @Test
    fun `浮动奖无公告金额_仍算中奖`() {
        assertTrue(result(0L, mapOf(PrizeTier.FIRST to 1L)).hasWon)
        assertTrue(result(0L, mapOf(PrizeTier.SECOND to 2L)).hasWon)
    }

    @Test
    fun `未中任何奖级_不算中奖`() {
        assertFalse(result(0L, emptyMap()).hasWon)
    }

    @Test
    fun `固定奖按金额正常中奖`() {
        assertTrue(result(3_000L, mapOf(PrizeTier.THIRD to 1L)).hasWon)
    }
}
