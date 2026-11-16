package com.ssqpick.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCodecTest {

    private fun ticket(index: Int) = BackupTicket(
        createdAtEpochMs = 1_700_000_000_000L + index,
        label = "守号 $index",
        redPicked = listOf(1, 2, 3, 4, 5, 6),
        bluePicked = listOf(7),
    )

    @Test
    fun 备份可以完整往返() {
        val bundle = BackupBundle(
            exportedAtEpochMs = 1_700_000_000_000L,
            tickets = listOf(ticket(0), ticket(1)),
            purchases = listOf(
                BackupPurchase(ticketIndex = 0, purchasedAtEpochMs = 1_700_000_100_000L, costYuan = 2),
                BackupPurchase(ticketIndex = 1, purchasedAtEpochMs = 1_700_000_200_000L, costYuan = 4),
            ),
        )
        val decoded = BackupCodec.decode(BackupCodec.encode(bundle))
        assertEquals(bundle, decoded)
        assertEquals(TicketDraft(redPicked = setOf(1, 2, 3, 4, 5, 6), bluePicked = setOf(7)), decoded.tickets[0].toDraft())
    }

    @Test
    fun 不支持的版本会被拒绝() {
        val text = BackupCodec.encode(
            BackupBundle(exportedAtEpochMs = 1L, schema = 99),
        )
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(text) }
    }

    @Test
    fun 购买指向不存在的号码会被拒绝() {
        val text = BackupCodec.encode(
            BackupBundle(
                exportedAtEpochMs = 1L,
                tickets = emptyList(),
                purchases = listOf(BackupPurchase(0, 1_700_000_000_000L, 2)),
            ),
        )
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(text) }
    }

    @Test
    fun 非法号码会被拒绝() {
        val text = BackupCodec.encode(
            BackupBundle(
                exportedAtEpochMs = 1L,
                tickets = listOf(ticket(0).copy(redPicked = listOf(1, 2, 3))),
            ),
        )
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(text) }
    }

    @Test
    fun 坏_json_给出明确错误() {
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode("{oops") }
    }
}
