package com.ssqpick.feature.check

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssqpick.designsystem.component.NumberBall
import com.ssqpick.designsystem.theme.SsqTheme
import com.ssqpick.model.PrizeTier

/**
 * 对奖界面：拉取最新一期开奖，核对全部已保存号码并算出总奖金。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckScreen(
    viewModel: CheckViewModel,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    SsqTheme {
        Column(modifier = modifier.fillMaxSize()) {
            TopAppBar(title = { Text("对奖") })
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("开奖核对", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                ui.target?.let { "核对目标：第 ${it.code} 期 ${it.date}" } ?: "尚无开奖数据",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            ui.target?.let { target ->
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    target.red.sorted().forEach {
                                        NumberBall(it, isBlue = false, size = 34.dp)
                                    }
                                    NumberBall(target.blue, isBlue = true, size = 34.dp)
                                }
                            }
                            ui.message?.let {
                                Spacer(Modifier.height(8.dp))
                                Text(it, style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = viewModel::syncAndCheck,
                                enabled = !ui.checking,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(if (ui.checking) "同步中…" else "同步并对奖") }
                        }
                    }
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("合计中奖", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "%,d 元".format(ui.totalAmountYuan),
                                style = MaterialTheme.typography.displaySmall,
                                color = if (ui.totalAmountYuan > 0) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            Text(
                                "${ui.results.size} 注中有 ${ui.winningTickets} 注中奖",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                items(ui.results, key = { it.ticketId }) { r ->
                    ResultCard(r, viewModel::tierLines)
                }
            }
        }
    }
}

@Composable
private fun ResultCard(
    result: com.ssqpick.model.TicketCheckResult,
    tierLines: (com.ssqpick.model.TicketCheckResult) -> List<String>,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("号码 #${result.ticketId}", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (result.hasWon) "${result.topTier?.label} · %,d 元".format(result.totalAmountYuan)
                    else "未中奖",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (result.hasWon) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (result.hasWon) {
                Spacer(Modifier.height(6.dp))
                tierLines(result).forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
