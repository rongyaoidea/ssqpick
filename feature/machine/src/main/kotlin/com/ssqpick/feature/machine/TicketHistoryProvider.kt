package com.ssqpick.feature.machine

import com.ssqpick.store.DrawRepository
import com.ssqpick.model.Draw
import com.ssqpick.model.TicketDraft

/**
 * 摇号结果的历史战绩查询。
 *
 * 刻意不把 repository 直接塞进 ViewModel——界面只需要"查历史"这个能力，
 * 换成别的数据源（缓存、假数据）不影响 ViewModel。
 */
class TicketHistoryProvider(
    private val drawRepository: DrawRepository,
) {
    suspend fun history(draft: TicketDraft): List<Draw> = drawRepository.all()
}
