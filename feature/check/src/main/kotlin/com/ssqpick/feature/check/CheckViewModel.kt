package com.ssqpick.feature.check

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssqpick.model.Draw
import com.ssqpick.model.PrizeTier
import com.ssqpick.model.TicketCheckResult
import com.ssqpick.rules.TicketRules
import com.ssqpick.rules.ticketsForDraw
import com.ssqpick.store.DrawRepository
import com.ssqpick.store.SyncResult
import com.ssqpick.store.TicketRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 对奖界面状态。 */
data class CheckUiState(
    val target: Draw? = null,
    val checking: Boolean = false,
    val message: String? = null,
    val results: List<TicketCheckResult> = emptyList(),
    /** 本地有、但对应的是别的期、因而没有参与本次核对的票数。 */
    val skippedCount: Int = 0,
    val totalAmountYuan: Long = 0L,
) {
    val winningTickets: Int get() = results.count { it.hasWon }
}

/**
 * 对奖与奖金计算。
 *
 * 关键点：一、二等奖是浮动奖金，单注金额必须取开奖公告的实付额
 * （[Draw.prizeGrades]），不能用固定值。固定奖（三~六等）才是定额。
 * 未同步到某一期时，该项只能显示奖级而无法给出金额。
 */
class CheckViewModel(
    private val ticketRepository: TicketRepository,
    private val drawRepository: DrawRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(CheckUiState())
    val ui: StateFlow<CheckUiState> = _ui.asStateFlow()

    init {
        // 目标期号跟随开奖库：后台 Worker 拉到新一期后，重进本页不用手动同步也能看到最新
        viewModelScope.launch {
            drawRepository.observeNewest().collect { newest ->
                _ui.value = _ui.value.copy(target = newest)
            }
        }
    }

    /** 同步最新开奖数据，然后立即对奖。 */
    fun syncAndCheck() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(checking = true, message = "正在同步开奖数据…")
            when (val r = drawRepository.syncFromOfficial()) {
                is SyncResult.Success -> {
                    _ui.value = _ui.value.copy(message = "新增 ${r.imported} 期")
                    checkAgainstLatest()
                }

                SyncResult.AlreadyUpToDate -> {
                    checkAgainstLatest()
                }

                is SyncResult.Failure -> {
                    _ui.value = _ui.value.copy(checking = false, message = "同步失败：${r.reason}")
                }
            }
        }
    }

    /**
     * 对本地最新一期开奖核对**属于这一期**的已保存号码。
     *
     * 每注号码都绑定"保存时刻之后的第一期开奖"（见 [ticketsForDraw]）：
     * 存给下一期的票不参与本期核对——拿上一期的开奖结果去对，只会得出
     * 一个没有意义的"未中奖"。
     */
    fun checkAgainstLatest() {
        viewModelScope.launch {
            val draws = drawRepository.all()
            if (draws.isEmpty()) {
                _ui.value = _ui.value.copy(
                    checking = false,
                    results = emptyList(),
                    skippedCount = 0,
                    message = "本地没有开奖数据，请先同步",
                )
                return@launch
            }
            val asc = draws.sortedBy { it.date }
            val target = asc.last()
            val (mine, others) = ticketsForDraw(ticketRepository.all(), target, asc)
            val results = mine.map { ticket -> evaluate(ticket.id, ticket.draft, target) }
            _ui.value = _ui.value.copy(
                target = target,
                checking = false,
                results = results,
                skippedCount = others.size,
                totalAmountYuan = results.sumOf { it.totalAmountYuan },
                message = "第 ${target.code} 期：核对 ${mine.size} 注" +
                    if (others.isEmpty()) "" else "，另有 ${others.size} 注不属于本期",
            )
        }
    }

    private fun evaluate(id: Long, draft: com.ssqpick.model.TicketDraft, draw: Draw): TicketCheckResult {
        val (total, breakdown) = TicketRules.evaluate(draft, draw)
        val amounts = breakdown.keys.associateWith { tier -> TicketRules.amountFor(tier, draw) }
        return TicketCheckResult(
            ticketId = id,
            drawCode = draw.code,
            totalAmountYuan = total,
            winningBetCount = breakdown.values.sum(),
            tierCounts = breakdown,
            tierAmounts = amounts,
        )
    }

    /** 展示某张票各奖级明细。 */
    fun tierLines(result: TicketCheckResult): List<String> = result.tierCounts.entries
        .sortedByDescending { it.key.rank }
        .map { (tier, count) ->
            val amount = result.tierAmounts[tier] ?: 0L
            val amountText = if (tier.isFloating) {
                if (amount > 0) "单注 $amount 元" else "金额待开奖公告"
            } else {
                "$amount 元"
            }
            "${tier.label} × $count 注（$amountText）"
        }
}
