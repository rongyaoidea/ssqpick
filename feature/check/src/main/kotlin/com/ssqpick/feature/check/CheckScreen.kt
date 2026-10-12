package com.ssqpick.feature.check

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssqpick.designsystem.component.BallRow
import com.ssqpick.designsystem.sketch.SketchButton
import com.ssqpick.designsystem.sketch.SketchCard
import com.ssqpick.designsystem.sketch.SketchChip
import com.ssqpick.designsystem.sketch.SketchDivider
import com.ssqpick.designsystem.sketch.SketchPalette
import com.ssqpick.designsystem.sketch.SketchSectionTitle
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
    // 只铺中奖的：未中奖的号码在下方"已保存的号码"里已有"未中奖"徽标，
    // 逐条再渲染一遍结果卡只是重复信息
    val winners = ui.results.filter { it.hasWon }

    LazyColumn(
        modifier = modifier
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
        } else {
            items(winners, key = { it.ticketId }) { r ->
                Column(Modifier.padding(horizontal = 14.dp)) {
                    ResultCard(r, viewModel::tierLines)
                    Spacer(Modifier.height(12.dp))
                }
            }
        }

        // 已保存的号码：票行自带状态与期号，对奖结果的全量视图在这里
        // （上面的结果卡只列中奖的，不重复未中奖的行）
        item {
            Spacer(Modifier.height(4.dp))
            savedTickets()
            Spacer(Modifier.height(24.dp))
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
    SketchCard(seed = "check-target", tilt = -0.3f) {
        SketchSectionTitle("开奖核对")
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
                label = if (result.totalAmountYuan > 0) {
                    "${result.topTier?.label} · %,d 元".format(result.totalAmountYuan)
                } else {
                    result.topTier?.label ?: "中奖"
                },
                seed = "check-chip-${result.ticketId}",
                fill = SketchPalette.Highlighter,
            )
        }
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            tierLines(result).forEach { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SketchPalette.Ink,
                )
            }
            SketchDivider(seed = "check-div-${result.ticketId}")
            Text(
                "小计 %,d 元（共 ${result.winningBetCount} 注）".format(result.totalAmountYuan),
                style = MaterialTheme.typography.bodyMedium,
                color = SketchPalette.Ink,
            )
        }
    }
}
