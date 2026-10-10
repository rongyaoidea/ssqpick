package com.ssqpick.store

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.ssqpick.model.Draw
import com.ssqpick.model.PrizeTier
import com.ssqpick.model.Purchase
import com.ssqpick.model.Ticket
import com.ssqpick.model.TicketDraft
import com.ssqpick.store.db.DrawQueries
import com.ssqpick.store.db.PurchaseQueries
import com.ssqpick.store.db.TicketQueries
import com.ssqpick.store.mapper.encode
import com.ssqpick.store.mapper.toDraft
import com.ssqpick.store.mapper.toDraw
import com.ssqpick.store.mapper.toPurchase
import com.ssqpick.store.network.CwlApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.ssqpick.store.db.Ticket as TicketRow

/** 同步结果，供 UI 明确告知用户发生了什么。 */
sealed interface SyncResult {
    data class Success(val imported: Int, val totalDraws: Long) : SyncResult
    data object AlreadyUpToDate : SyncResult
    data class Failure(val reason: String) : SyncResult
}

interface DrawRepository {
    fun observeAll(): Flow<List<Draw>>
    fun observeNewest(): Flow<Draw?>
    suspend fun all(): List<Draw>
    suspend fun count(): Long
    suspend fun insertAll(draws: List<Draw>)

    /** 从官方接口增量同步。 */
    suspend fun syncFromOfficial(): SyncResult
}

class DrawRepositoryImpl(
    private val api: CwlApi,
    private val drawQueries: DrawQueries,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : DrawRepository {

    override fun observeAll(): Flow<List<Draw>> = drawQueries.selectAllDraws().asFlow().mapToList(io).map { rows -> rows.map { it.toDraw() } }

    override fun observeNewest(): Flow<Draw?> = drawQueries.selectNewestDraw().asFlow().mapToOneOrNull(io).map { it?.toDraw() }

    override suspend fun all(): List<Draw> = withContext(io) {
        drawQueries.selectAllDraws().executeAsList().map { it.toDraw() }
    }

    override suspend fun count(): Long = withContext(io) {
        drawQueries.selectDrawCount().executeAsOne()
    }

    override suspend fun insertAll(draws: List<Draw>) = withContext(io) {
        drawQueries.transaction {
            draws.forEach { d ->
                drawQueries.insertOrIgnoreDraw(
                    code = d.code,
                    date = d.date.toString(),
                    red = d.red.encode(),
                    blue = d.blue.toLong(),
                    sales = d.sales,
                    pool_amount = d.poolAmount,
                    first_prize_amount = d.prizeGrades
                        .firstOrNull { it.tier == PrizeTier.FIRST }?.amountPerTicket ?: 0L,
                    second_prize_amount = d.prizeGrades
                        .firstOrNull { it.tier == PrizeTier.SECOND }?.amountPerTicket ?: 0L,
                )
            }
        }
    }

    /**
     * 增量同步。
     *
     * 官方提供 2013 至今约 2000+ 期（实测 issueCount 放大即可一次取回）。
     * 两种路径：
     * - **全量回填**：库为空、或本地期数明显不足（老版本只同步过 500 期），
     *   一次请求取回全部历史；
     * - **增量**：其余情况只取最近 500 期，按日期筛出新增。
     *
     * 新增期数用"前后计数差"计算，而不是 remote.size——回填时库里已有部分
     * 数据，insertOrIgnore 会去重，按 remote.size 报数会说谎。
     */
    override suspend fun syncFromOfficial(): SyncResult = withContext(io) {
        runCatching {
            val localNewest = drawQueries.selectNewestDraw().executeAsOneOrNull()
            val before = drawQueries.selectDrawCount().executeAsOne()

            val backfill = localNewest == null || before < BACKFILL_MIN_DRAWS
            val remote = if (backfill) {
                api.fetchFullHistory()
            } else {
                api.fetchRecent(issueCount = RECENT_WINDOW).sortedByDescending { it.date }
            }
            if (remote.isEmpty()) return@withContext SyncResult.Failure("接口未返回开奖数据")

            // 回填时全量交给 insertOrIgnore 去重；增量时必须先按日期筛，
            // 否则把 500 期整批重插一遍（虽然无害，但白写几十个事务步骤）
            val fresh = if (backfill || localNewest == null) {
                remote
            } else {
                remote.filter { it.date.toString() > localNewest.date.toString() }
            }
            if (fresh.isEmpty()) return@withContext SyncResult.AlreadyUpToDate

            insertAll(fresh)
            val total = drawQueries.selectDrawCount().executeAsOne()
            val imported = (total - before).toInt()
            if (imported <= 0) {
                SyncResult.AlreadyUpToDate
            } else {
                SyncResult.Success(imported, total)
            }
        }.getOrElse { e -> SyncResult.Failure(e.message ?: e::class.simpleName ?: "未知错误") }
    }

    companion object {
        /** 增量窗口：最近 500 期（约 3.2 年），足够覆盖任何合理的同步间隔。 */
        const val RECENT_WINDOW = 500

        /** 本地不足这个数视为历史不完整，触发全量回填（官方现有 2000+ 期）。 */
        const val BACKFILL_MIN_DRAWS = 1_200L
    }
}

interface TicketRepository {
    fun observeAll(): Flow<List<Ticket>>
    suspend fun all(): List<Ticket>
    suspend fun save(draft: TicketDraft, label: String? = null): Long
    suspend fun rename(id: Long, label: String?)
    suspend fun delete(id: Long)
}

class TicketRepositoryImpl(
    private val ticketQueries: TicketQueries,
    private val purchaseQueries: PurchaseQueries,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : TicketRepository {

    private fun rowToTicket(row: TicketRow) = Ticket(
        id = row.id,
        createdAtEpochMs = row.created_at,
        label = row.label,
        draft = row.toDraft(),
    )

    override fun observeAll(): Flow<List<Ticket>> = ticketQueries.selectAllTickets().asFlow().mapToList(io).map { rows -> rows.map(::rowToTicket) }

    override suspend fun all(): List<Ticket> = withContext(io) {
        ticketQueries.selectAllTickets().executeAsList().map(::rowToTicket)
    }

    override suspend fun save(draft: TicketDraft, label: String?): Long = withContext(io) {
        ticketQueries.transactionWithResult {
            ticketQueries.insertTicket(
                created_at = System.currentTimeMillis(),
                label = label,
                red_bold = draft.redBold.toList().encode(),
                red_picked = draft.redPicked.toList().encode(),
                blue_bold = draft.blueBold.toList().encode(),
                blue_picked = draft.bluePicked.toList().encode(),
            )
            // insert 返回受影响行数，拿真正的自增 id 要靠 last_insert_rowid
            ticketQueries.selectLastInsertRowId().executeAsOne()
        }
    }

    override suspend fun rename(id: Long, label: String?) {
        withContext(io) { ticketQueries.updateTicketLabel(label, id).value }
    }

    override suspend fun delete(id: Long) {
        // 票与其购买记录一起删：孤儿购买既没法展示也结不了算
        withContext(io) {
            ticketQueries.transaction {
                purchaseQueries.deletePurchasesForTicket(id)
                ticketQueries.deleteTicket(id)
            }
        }
    }
}

interface PurchaseRepository {
    fun observeAll(): Flow<List<Purchase>>

    /** 未结算（还没有开奖结果）的购买，后台结算用。 */
    suspend fun unsettled(): List<Purchase>
    suspend fun record(ticketId: Long, costYuan: Long)
    suspend fun settle(ids: List<Long>)
    suspend fun delete(id: Long)
}

class PurchaseRepositoryImpl(
    private val purchaseQueries: PurchaseQueries,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : PurchaseRepository {

    override fun observeAll(): Flow<List<Purchase>> = purchaseQueries.selectAllPurchases().asFlow().mapToList(io).map { rows -> rows.map { it.toPurchase() } }

    override suspend fun unsettled(): List<Purchase> = withContext(io) {
        purchaseQueries.selectUnsettledPurchases().executeAsList().map { it.toPurchase() }
    }

    override suspend fun record(ticketId: Long, costYuan: Long) {
        withContext(io) {
            purchaseQueries.insertPurchase(
                ticket_id = ticketId,
                purchased_at = System.currentTimeMillis(),
                cost_yuan = costYuan,
            )
        }
    }

    override suspend fun settle(ids: List<Long>) {
        if (ids.isEmpty()) return
        withContext(io) {
            purchaseQueries.transaction {
                ids.forEach { purchaseQueries.markPurchaseSettled(it) }
            }
        }
    }

    override suspend fun delete(id: Long) {
        withContext(io) { purchaseQueries.deletePurchase(id) }
    }
}
