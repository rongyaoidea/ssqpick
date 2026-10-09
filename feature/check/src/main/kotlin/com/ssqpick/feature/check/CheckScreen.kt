package com.ssqpick.feature.check

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssqpick.designsystem.component.BallRow
import com.ssqpick.designsystem.component.EmptyHint
import com.ssqpick.designsystem.component.InfoChip
import com.ssqpick.designsystem.component.NumberBall
import com.ssqpick.designsystem.component.ScreenColumn
import com.ssqpick.designsystem.component.SsqCard
import com.ssqpick.designsystem.theme.SsqDimens

/**
 * 对奖：拉取最新一期开奖，核对全部已保存号码并算出总奖金。
 */
@Composable
fun CheckScreen(
    viewModel: CheckViewModel,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        item {
            ScreenColumn {
                TargetCard(
                    target = ui.target,
                    message = ui.message,
                    checking = ui.checking,
                    onSync = viewModel::syncAndCheck,
                )
            }
        }

        item {
            ScreenColumn {
                SummaryCard(
                    totalAmountYuan = ui.totalAmountYuan,
                    ticketCount = ui.results.size,
                    winningCount = ui.winningTickets,
                )
            }
        }

        if (ui.results.isEmpty()) {
            item {
                ScreenColumn {
                    EmptyHint("点上面的“同步并对奖”，核对本地保存的全部号码。")
                }
            }
        } else {
            items(ui.results, key = { it.ticketId }) { r ->
                ScreenColumn {
                    ResultCard(r, viewModel::tierLines)
                }
            }
        }
    }
}

@Composable
private fun TargetCard(
    target: com.ssqpick.model.Draw?,
    message: String?,
    checking: Boolean,
    onSync: () -> Unit,
) {
    SsqCard(title = "开奖核对", container = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            target?.let { "核对目标：第 ${it.code} 期 ${it.date}" } ?: "尚无开奖数据",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (target != null) {
            BallRow(numbers = target.red.sorted(), isBlue = false, ballSize = SsqDimens.ballSizeCompact)
            BallRow(numbers = listOf(target.blue), isBlue = true, ballSize = SsqDimens.ballSizeCompact)
        }
        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        Text(
            "一、二等奖为浮动奖金，金额取开奖公告的实付额；三至六等为固定奖金。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onSync,
            enabled = !checking,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (checking) "同步中…" else "同步并对奖") }
    }
}

@Composable
private fun SummaryCard(
    totalAmountYuan: Long,
    ticketCount: Int,
    winningCount: Int,
) {
    SsqCard(title = "合计中奖") {
        Text(
            "%,d 元".format(totalAmountYuan),
            style = MaterialTheme.typography.displaySmall,
            color = if (totalAmountYuan > 0) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Text(
            "$ticketCount 注中有 $winningCount 注中奖",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResultCard(
    result: com.ssqpick.model.TicketCheckResult,
    tierLines: (com.ssqpick.model.TicketCheckResult) -> List<String>,
) {
    SsqCard(
        trailing = {
            InfoChip(
                text = if (result.hasWon) {
                    "${result.topTier?.label} · %,d 元".format(result.totalAmountYuan)
                } else {
                    "未中奖"
                },
                container = if (result.hasWon) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            )
        },
    ) {
        Text("号码 #${result.ticketId}", style = MaterialTheme.typography.titleMedium)
        if (result.hasWon) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                tierLines(result).forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider()
                Text(
                    "小计 %,d 元（共 ${result.winningBetCount} 注）".format(result.totalAmountYuan),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            Text(
                "本期未命中任何奖级。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
