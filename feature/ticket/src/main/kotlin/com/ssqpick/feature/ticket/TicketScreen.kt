package com.ssqpick.feature.ticket

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssqpick.designsystem.component.NumberBall
import com.ssqpick.designsystem.theme.SsqTheme
import com.ssqpick.rules.TicketRules

/**
 * 手选号码界面：点格选红球/蓝球，支持单式与复式，可保存并查看历史战绩。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketScreen(
    viewModel: TicketViewModel,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val tickets by viewModel.tickets.collectAsStateWithLifecycle()

    SsqTheme {
        Column(modifier = modifier.fillMaxSize()) {
            TopAppBar(title = { Text("选号") })
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    SyncCard(
                        drawCount = ui.drawCount,
                        message = ui.syncMessage,
                        newest = ui.newestDraw,
                        onSync = viewModel::syncDraws,
                    )
                }

                item {
                    DraftCard(
                        ui = ui,
                        onToggleRed = viewModel::toggleRed,
                        onToggleBlue = viewModel::toggleBlue,
                        onClear = viewModel::clear,
                        onRandom = viewModel::randomFill,
                        onSave = { viewModel.save() },
                    )
                }

                item {
                    Text(
                        "已保存的号码（${tickets.size}）",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                items(tickets, key = { it.id }) { ticket ->
                    TicketRow(ticket = ticket, onDelete = { viewModel.delete(ticket) })
                }

                if (tickets.isEmpty()) {
                    item {
                        Text(
                            "还没有保存的号码。摇号或手选后点“保存这注”。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("开奖数据", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "本地已存 $drawCount 期" +
                    (newest?.let { " · 最新 ${it.code} 期 ${it.date}" } ?: " · 尚未同步"),
                style = MaterialTheme.typography.bodyMedium,
            )
            message?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onSync) { Text("从官方同步开奖数据") }
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
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(draft.kind.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${draft.betCount} 注 / ${draft.priceYuan} 元",
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            if (!ui.isValid) {
                Spacer(Modifier.height(4.dp))
                Text(
                    ui.validation.message ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(12.dp))
            Text("红球（1-33）", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(6.dp))
            NumberGrid(
                range = 1..33,
                selected = draft.redPool,
                onToggle = onToggleRed,
            )

            Spacer(Modifier.height(12.dp))
            Text("蓝球（1-16）", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(6.dp))
            NumberGrid(
                range = 1..16,
                selected = draft.bluePool,
                onToggle = onToggleBlue,
            )

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSave, enabled = ui.isValid, modifier = Modifier.weight(1f)) {
                    Text("保存这注")
                }
                OutlinedButton(onClick = onRandom, modifier = Modifier.weight(1f)) { Text("机选") }
                OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) { Text("清空") }
            }
        }
    }
}

/** 号码选择网格，每行固定数量，宽高比一致。 */
@Composable
private fun NumberGrid(
    range: IntRange,
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
    perRow: Int = 6,
) {
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
                        isBlue = range.last == 16,
                        dimmed = n !in selected,
                        size = 38.dp,
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
private fun TicketRow(ticket: com.ssqpick.model.Ticket, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ticket.draft.redPool.sorted().forEach {
                        NumberBall(it, isBlue = false, size = 30.dp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ticket.draft.bluePool.sorted().forEach {
                        NumberBall(it, isBlue = true, size = 30.dp)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "${TicketRules.betCount(ticket.draft)} 注 · ${ticket.draft.kind.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onDelete) { Text("删除") }
        }
    }
}
