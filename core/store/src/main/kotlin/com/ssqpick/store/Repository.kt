package com.ssqpick.store

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.ssqpick.model.Draw
import com.ssqpick.model.PrizeTier
import com.ssqpick.model.Ticket
import com.ssqpick.model.TicketDraft
import com.ssqpick.store.db.DrawQueries
import com.ssqpick.store.db.TicketQueries
import com.ssqpick.store.mapper.encode
import com.ssqpick.store.mapper.toDraft
import com.ssqpick.store.mapper.toDraw
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
    suspend fun clear()

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

    override suspend fun clear() {
        withContext(io) { drawQueries.deleteAllDraws().value }
    }

    /**
     * 增量同步。
     *
     * 官方接口返回最近若干期，按库里最新期号筛出真正新增的部分。
     * 首次运行时库为空，会一次性导入全部历史（官方仅提供 2013 年起数据）。
     */
    override suspend fun syncFromOfficial(): SyncResult = withContext(io) {
        runCatching {
            val localNewest = drawQueries.selectNewestDraw().executeAsOneOrNull()
            val remote = api.fetchRecent(issueCount = 500).sortedByDescending { it.date }
            if (remote.isEmpty()) return@withContext SyncResult.Failure("接口未返回开奖数据")

            val fresh = if (localNewest == null) {
                remote
            } else {
                remote.filter { it.date.toString() > localNewest.date.toString() }
            }
            if (fresh.isEmpty()) return@withContext SyncResult.AlreadyUpToDate
            insertAll(fresh)
            SyncResult.Success(fresh.size, drawQueries.selectDrawCount().executeAsOne())
        }.getOrElse { e -> SyncResult.Failure(e.message ?: e::class.simpleName ?: "未知错误") }
    }
}

interface TicketRepository {
    fun observeAll(): Flow<List<Ticket>>
    suspend fun all(): List<Ticket>
    suspend fun save(draft: TicketDraft, label: String? = null): Long
    suspend fun rename(id: Long, label: String?)
    suspend fun delete(id: Long)
    suspend fun clear()
}

class TicketRepositoryImpl(
    private val ticketQueries: TicketQueries,
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
        withContext(io) { ticketQueries.deleteTicket(id).value }
    }

    override suspend fun clear() {
        withContext(io) {
            ticketQueries.transaction {
                ticketQueries.selectAllTickets().executeAsList().forEach {
                    ticketQueries.deleteTicket(it.id).value
                }
            }
        }
    }
}
