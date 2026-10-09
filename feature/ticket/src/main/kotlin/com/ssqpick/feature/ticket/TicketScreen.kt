package com.ssqpick.feature.ticket

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssqpick.designsystem.component.ActionRow
import com.ssqpick.designsystem.component.EmptyHint
import com.ssqpick.designsystem.component.InfoChip
import com.ssqpick.designsystem.component.NumberBall
import com.ssqpick.designsystem.component.ScreenColumn
import com.ssqpick.designsystem.component.SsqCard
import com.ssqpick.designsystem.theme.SsqDimens
import com.ssqpick.rules.TicketRules

/**
 * 手选号码：点格选号，支持单式与复式，保存后可查看历史战绩。
 *
 * 主题由 MainActivity 提供，这里不再包一层。
 */
@Composable
fun TicketScreen(
    viewModel: TicketViewModel,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val tickets by viewModel.tickets.collectAsStateWithLifecycle()
    val draft = ui.draft

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        item {
            ScreenColumn {
                SyncCard(
                    drawCount = ui.drawCount,
                    message = ui.syncMessage,
                    newest = ui.newestDraw,
                    onSync = viewModel::syncDraws,
                )
            }
        }

        item {
            ScreenColumn {
                DraftCard(
                    ui = ui,
                    onToggleRed = viewModel::toggleRed,
                    onToggleBlue = viewModel::toggleBlue,
                    onClear = viewModel::clear,
                    onRandom = viewModel::randomFill,
                    onSave = { viewModel.save() },
                )
            }
        }

        if (tickets.isNotEmpty()) {
            item {
                ScreenColumn {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("已保存的号码", style = MaterialTheme.typography.titleMedium)
                        InfoChip("${tickets.size} 张")
                    }
                }
            }
            items(tickets, key = { it.id }) { ticket ->
                ScreenColumn {
                    TicketRow(ticket = ticket, onDelete = { viewModel.delete(ticket) })
                }
            }
        } else {
            item {
                ScreenColumn {
                    EmptyHint("还没有保存的号码。摇号或手选后点“保存这注”。")
                }
            }
        }
    }
}

@Composable
private fun SyncCard(
    drawCount: Long,
    message: String?,
    newest: com.ssqpick.model.Draw?,
    onSync: () -> Unit,
) {
    SsqCard(title = "开奖数据", container = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            newest?.let { "已存 $drawCount 期 · 最新第 ${it.code} 期 ${it.date}" }
                ?: "本地尚无开奖数据",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "数据来源：中国福彩发行管理中心官方开奖公告。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        OutlinedButton(onClick = onSync, modifier = Modifier.fillMaxWidth()) {
            Text("从官方同步开奖数据")
        }
    }
}

@Composable
private fun DraftCard(
    ui: TicketUiState,
    onToggleRed: (Int) -> Unit,
    onToggleBlue: (Int) -> Unit,
    onClear: () -> Unit,
    onRandom: () -> Unit,
    onSave: () -> Unit,
) {
    val draft = ui.draft
    SsqCard(
        title = draft.kind.label,
        trailing = { InfoChip("${draft.betCount} 注 / ${draft.priceYuan} 元") },
    ) {
        if (!ui.isValid) {
            Text(
                ui.validation.message ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        SectionLabel("红球（1-33）", "${draft.redPool.size} 个")
        NumberGrid(
            range = 1..33,
            selected = draft.redPool,
            onToggle = onToggleRed,
        )

        SectionLabel("蓝球（1-16）", "${draft.bluePool.size} 个")
        NumberGrid(
            range = 1..16,
            selected = draft.bluePool,
            onToggle = onToggleBlue,
        )

        ActionRow {
            Button(
                onClick = onSave,
                enabled = ui.isValid,
                modifier = Modifier.weight(1f),
            ) { Text("保存这注") }
            OutlinedButton(onClick = onRandom, modifier = Modifier.weight(1f)) { Text("机选") }
            OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) { Text("清空") }
        }
    }
}

@Composable
private fun SectionLabel(text: String, count: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            count,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 号码网格。未选中的球渲染为暗色轮廓态，点任意位置即可切换。 */
@Composable
private fun NumberGrid(
    range: IntRange,
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
    perRow: Int = 6,
) {
    val isBlue = range.last == 16
    val numbers = range.toList()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        numbers.chunked(perRow).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { n ->
                    NumberBall(
                        number = n,
                        isBlue = isBlue,
                        size = SsqDimens.ballSizeCompact,
                        selected = n in selected,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onToggle(n) },
                    )
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun TicketRow(
    ticket: com.ssqpick.model.Ticket,
    onDelete: () -> Unit,
) {
    SsqCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ticket.draft.redPool.sorted().forEach {
                NumberBall(it, isBlue = false, size = SsqDimens.ballSizeSmall)
            }
        }
        Spacer(Modifier.height(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ticket.draft.bluePool.sorted().forEach {
                NumberBall(it, isBlue = true, size = SsqDimens.ballSizeSmall)
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${TicketRules.betCount(ticket.draft)} 注 · ${ticket.draft.kind.label}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onDelete) { Text("删除") }
        }
    }
}
