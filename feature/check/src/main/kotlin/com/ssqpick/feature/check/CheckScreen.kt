package com.ssqpick.feature.check

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssqpick.designsystem.component.BallRow
import com.ssqpick.designsystem.component.NumberBall
import com.ssqpick.designsystem.component.WonStamp
import com.ssqpick.designsystem.sketch.SketchButton
import com.ssqpick.designsystem.sketch.SketchCard
import com.ssqpick.designsystem.sketch.SketchChip
import com.ssqpick.designsystem.sketch.SketchDivider
import com.ssqpick.designsystem.sketch.SketchMiniButton
import com.ssqpick.designsystem.sketch.SketchPalette
import com.ssqpick.designsystem.sketch.SketchSectionTitle
import com.ssqpick.designsystem.sketch.SketchSheet
import com.ssqpick.designsystem.theme.SsqDimens

/**
 * 对奖：拉取最新一期开奖，核对全部已保存号码并算出总奖金。
 *
 * 结果区**只列中奖的号码**：未中奖的行不在这里重复"号码 #X 未中奖"，
 * 下方"已保存的号码"每行自带状态徽标，合计卡也已给出中奖注数。
 *
 * 手绘只作用在外壳；开奖号码与金额数字保持清晰，中奖标签用荧光笔底。
 */
@Composable
fun CheckScreen(
    viewModel: CheckViewModel,
    /** "已保存的号码"列表由 app 模块注入（它住在 feature:ticket，不反向依赖）。 */
    savedTickets: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var pickerOpen by remember { mutableStateOf(false) }
    var filterIdx by rememberSaveable { mutableIntStateOf(0) }
    val filter = CheckFilter.entries[filterIdx]
    val visibleResults = ui.results.filter {
        when (filter) {
            CheckFilter.WON -> it.hasWon
            CheckFilter.ALL -> true
            CheckFilter.MISSED -> !it.hasWon
        }
    }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(SketchPalette.Canvas),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            item {
                Column(Modifier.padding(horizontal = 14.dp)) {
                    Spacer(Modifier.height(6.dp))
                    TargetCard(
                        target = ui.target,
                        message = ui.message,
                        checking = ui.checking,
                        onSync = viewModel::syncAndCheck,
                        onPick = { pickerOpen = true },
                    )
                    Spacer(Modifier.height(14.dp))
                }
            }

            item {
                Column(Modifier.padding(horizontal = 14.dp)) {
                    SummaryCard(
                        totalAmountYuan = ui.totalAmountYuan,
                        ticketCount = ui.results.size,
                        winningCount = ui.winningTickets,
                    )
                    if (ui.skippedCount > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "另有 ${ui.skippedCount} 注对应其他期，未参与本次核对。",
                            style = MaterialTheme.typography.bodySmall,
                            color = SketchPalette.InkSoft,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CheckFilter.entries.forEach { item ->
                            SketchMiniButton(
                                label = item.label,
                                seed = "check-filter-${item.name}",
                                onClick = { filterIdx = item.ordinal },
                                filled = item == filter,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
            }

            if (ui.results.isEmpty()) {
                item {
                    Column(Modifier.padding(horizontal = 14.dp)) {
                        SketchCard(seed = "check-empty", tilt = 0.3f) {
                            Text(
                                "点上面的“同步并对奖”，核对本地保存的、属于本期的号码。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SketchPalette.InkSoft,
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                }
            } else if (visibleResults.isEmpty()) {
                item {
                    Column(Modifier.padding(horizontal = 14.dp)) {
                        SketchCard(seed = "check-filter-empty", tilt = 0.2f) {
                            Text(
                                "没有“${filter.label}”的结果。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SketchPalette.InkSoft,
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                }
            } else {
                items(visibleResults, key = { it.ticketId }) { r ->
                    Column(Modifier.padding(horizontal = 14.dp)) {
                        ResultCard(r, viewModel::tierLines)
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                savedTickets()
                Spacer(Modifier.height(24.dp))
            }
        }

        if (pickerOpen) {
            DrawPickerSheet(
                draws = ui.availableDraws,
                selectedCode = ui.selectedCode,
                onDismiss = { pickerOpen = false },
                onSelect = { code ->
                    pickerOpen = false
                    viewModel.checkAgainst(code)
                },
            )
        }
    }
}

private enum class CheckFilter(val label: String) {
    WON("中奖"),
    ALL("全部"),
    MISSED("未中"),
}

@Composable
private fun DrawPickerSheet(
    draws: List<com.ssqpick.model.Draw>,
    selectedCode: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    SketchSheet(onDismiss = onDismiss) { dismiss ->
        Text("选择核对期号", style = MaterialTheme.typography.titleMedium, color = SketchPalette.Ink)
        Spacer(Modifier.height(8.dp))
        if (draws.isEmpty()) {
            Text("本地还没有开奖数据。", style = MaterialTheme.typography.bodyMedium, color = SketchPalette.InkSoft)
        } else {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(draws.take(120), key = { it.code }) { draw ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                dismiss()
                                onSelect(draw.code)
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "第 ${draw.code} 期",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (draw.code == selectedCode) SketchPalette.RedInk else SketchPalette.Ink,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            draw.date.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = SketchPalette.InkSoft,
                        )
                    }
                    SketchDivider(seed = "pick-${draw.code}")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        SketchMiniButton(label = "只看最新", seed = "check-latest", onClick = {
            dismiss()
            onSelect(null)
        }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun TargetCard(
    target: com.ssqpick.model.Draw?,
    message: String?,
    checking: Boolean,
    onSync: () -> Unit,
    onPick: () -> Unit,
) {
    SketchCard(seed = "check-target", tilt = -0.3f) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SketchSectionTitle("开奖核对", modifier = Modifier.weight(1f))
            SketchMiniButton(label = "换一期", seed = "check-pick", onClick = onPick)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            target?.let { "核对目标：第 ${it.code} 期 ${it.date}" } ?: "尚无开奖数据",
            style = MaterialTheme.typography.bodyMedium,
            color = SketchPalette.Ink,
        )
        if (target != null) {
            Spacer(Modifier.height(8.dp))
            BallRow(numbers = target.red.sorted(), isBlue = false, ballSize = SsqDimens.ballSizeCompact)
            Spacer(Modifier.height(4.dp))
            BallRow(numbers = listOf(target.blue), isBlue = true, ballSize = SsqDimens.ballSizeCompact)
        }
        message?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = SketchPalette.Ink)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "一、二等奖为浮动奖金，金额取开奖公告的实付额；三至六等为固定奖金。",
            style = MaterialTheme.typography.bodySmall,
            color = SketchPalette.InkSoft,
        )
        Spacer(Modifier.height(10.dp))
        SketchButton(
            label = if (checking) "同步中…" else "同步并对奖",
            onClick = onSync,
            enabled = !checking,
            seed = "check-sync",
            tilt = -0.4f,
        )
    }
}

@Composable
private fun SummaryCard(
    totalAmountYuan: Long,
    ticketCount: Int,
    winningCount: Int,
) {
    SketchCard(seed = "check-summary", tilt = 0.25f) {
        SketchSectionTitle("合计中奖")
        Spacer(Modifier.height(8.dp))
        Text(
            "%,d 元".format(totalAmountYuan),
            style = MaterialTheme.typography.displaySmall,
            color = if (totalAmountYuan > 0) SketchPalette.RedInk else SketchPalette.InkSoft,
        )
        Text(
            "$ticketCount 注中有 $winningCount 注中奖",
            style = MaterialTheme.typography.bodyMedium,
            color = SketchPalette.InkSoft,
        )
    }
}

/** 中奖号码的结果卡：奖级明细 + 小计。未中奖的号码不在这里重复（见存票列表）。 */
@Composable
private fun ResultCard(
    result: com.ssqpick.model.TicketCheckResult,
    tierLines: (com.ssqpick.model.TicketCheckResult) -> List<String>,
) {
    SketchCard(
        seed = "check-${result.ticketId}",
        tilt = if (result.ticketId % 2L == 0L) 0.25f else -0.25f,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "号码 #${result.ticketId}",
                style = MaterialTheme.typography.titleMedium,
                color = SketchPalette.Ink,
            )
            SketchChip(
                // 一二等奖无公告时金额为 0：只报奖级，不编金额
                label = when {
                    !result.hasWon -> "未中奖"
                    result.totalAmountYuan > 0 -> "${result.topTier?.label} · %,d 元".format(result.totalAmountYuan)
                    else -> result.topTier?.label ?: "中奖"
                },
                seed = "check-chip-${result.ticketId}",
                fill = if (result.hasWon) SketchPalette.Highlighter else null,
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth()) {
            Column {
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    result.draft.redPool.sorted().forEach {
                        NumberBall(it, isBlue = false, size = SsqDimens.ballSizeSmall)
                    }
                }
                Spacer(Modifier.height(2.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    result.draft.bluePool.sorted().forEach {
                        NumberBall(it, isBlue = true, size = SsqDimens.ballSizeSmall)
                    }
                }
            }
            if (result.hasWon) {
                WonStamp(
                    tierLabel = result.topTier?.label,
                    amountYuan = result.totalAmountYuan,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (result.hasWon) {
                tierLines(result).forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SketchPalette.Ink,
                    )
                }
            } else {
                Text(
                    "这一期没有命中任何奖级。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SketchPalette.InkSoft,
                )
            }
            SketchDivider(seed = "check-div-${result.ticketId}")
            Text(
                if (result.hasWon) {
                    "小计 %,d 元（共 ${result.winningBetCount} 注）".format(result.totalAmountYuan)
                } else {
                    "未中奖"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (result.hasWon) SketchPalette.Ink else SketchPalette.InkSoft,
            )
        }
    }
}
