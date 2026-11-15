package com.ssqpick.feature.data

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssqpick.designsystem.sketch.SketchBarRow
import com.ssqpick.designsystem.sketch.SketchButton
import com.ssqpick.designsystem.sketch.SketchCard
import com.ssqpick.designsystem.sketch.SketchDivider
import com.ssqpick.designsystem.sketch.SketchMetaRow
import com.ssqpick.designsystem.sketch.SketchMiniButton
import com.ssqpick.designsystem.sketch.SketchPalette
import com.ssqpick.designsystem.sketch.SketchSectionTitle
import com.ssqpick.designsystem.sketch.sketchBox
import com.ssqpick.model.PrizeTier
import com.ssqpick.model.TicketDraft
import com.ssqpick.rules.sim.SimFan
import com.ssqpick.rules.sim.SimResult
import com.ssqpick.rules.sim.SimSource
import com.ssqpick.rules.sim.SimStrategy
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 模拟实验室（全屏）。
 *
 * 不预测下一期：只把“如果一直这样买”的分布、回撤和预算耗尽概率摊开。
 * 所有结果都带边界说明；没有“幸运号”“策略评分”这类暗示。
 */
@Composable
fun SimLabScreen(
    viewModel: SimLabViewModel,
    currentDraft: () -> TicketDraft?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    BackHandler(onBack = onClose)
    LaunchedEffect(Unit) { viewModel.setDraft(currentDraft()) }
    val scroll = rememberScrollState()
    val resultRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(ui.result != null) {
        if (ui.result != null) resultRequester.bringIntoView()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SketchPalette.Canvas)
            .verticalScroll(scroll)
            .padding(horizontal = 14.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "‹ 返回",
                style = MaterialTheme.typography.labelLarge,
                color = SketchPalette.InkSoft,
                modifier = Modifier
                    .clickable(role = Role.Button, onClick = onClose)
                    .padding(vertical = 6.dp, horizontal = 2.dp),
            )
            Spacer(Modifier.weight(1f))
            Text("模拟实验室", style = MaterialTheme.typography.titleLarge, color = SketchPalette.Ink)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "只描述“如果一直这样买”的分布，不预测下一期；策略不会改变单注概率。",
            style = MaterialTheme.typography.bodySmall,
            color = SketchPalette.InkSoft,
        )
        Spacer(Modifier.height(12.dp))

        SettingsCard(ui = ui, viewModel = viewModel)
        Spacer(Modifier.height(14.dp))

        ui.result?.let { result ->
            Column(Modifier.bringIntoViewRequester(resultRequester)) {
                SummaryCard(result = result)
                Spacer(Modifier.height(14.dp))
                FanCard(result)
                Spacer(Modifier.height(14.dp))
                TierCard(result)
                Spacer(Modifier.height(14.dp))
                HonestyCard()
                Spacer(Modifier.height(14.dp))
                SketchButton(
                    label = "换一批重跑",
                    onClick = {
                        viewModel.rerollSeed()
                        viewModel.run()
                    },
                    filled = false,
                    seed = "sim-rerun",
                    tilt = 0.3f,
                )
            }
        } ?: PlaceholderCard()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsCard(ui: SimLabUiState, viewModel: SimLabViewModel) {
    val enabled = !ui.running
    SketchCard(seed = "sim-settings", tilt = -0.2f) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SketchSectionTitle("实验设置", modifier = Modifier.weight(1f))
            Text(
                "${ui.paths} 次 × ${ui.drawsPerPath} 期",
                style = MaterialTheme.typography.labelMedium,
                color = SketchPalette.InkSoft,
            )
        }
        Spacer(Modifier.height(10.dp))

        SimChoiceRow(
            title = "开奖数据",
            options = listOf(
                Choice(SimSource.UNIFORM, "理论随机"),
                Choice(SimSource.HISTORICAL, "历史重放"),
            ),
            selected = ui.source,
            enabled = enabled,
            onSelect = viewModel::setSource,
        )
        if (ui.source == SimSource.HISTORICAL && ui.drawsAvailable == 0) {
            Spacer(Modifier.height(3.dp))
            Text(
                "还没有开奖数据：先在数据页同步，才能重放历史。",
                style = MaterialTheme.typography.labelSmall,
                color = SketchPalette.InkFaint,
            )
        }
        Spacer(Modifier.height(8.dp))
        SimChoiceRow(
            title = "号码",
            options = listOf(
                Choice(SimLabStrategyKind.RANDOM_SINGLE, "每期机选"),
                Choice(
                    SimLabStrategyKind.MY_DRAFT,
                    if (ui.draftBetCount > 0) "我的号码·${ui.draftBetCount}注" else "我的号码",
                ),
            ),
            selected = ui.strategy,
            enabled = enabled,
            optionEnabled = { it != SimLabStrategyKind.MY_DRAFT || ui.draftValid },
            onSelect = viewModel::setStrategy,
        )
        if (!ui.draftValid) {
            Spacer(Modifier.height(3.dp))
            Text(
                "选号页还没有合法号码；“我的号码”暂不可用。",
                style = MaterialTheme.typography.labelSmall,
                color = SketchPalette.InkFaint,
            )
        }
        Spacer(Modifier.height(8.dp))
        SimChoiceRow(
            title = "周期",
            options = listOf(
                Choice(156, "1 年"),
                Choice(780, "5 年"),
                Choice(1_560, "10 年"),
            ),
            selected = ui.drawsPerPath,
            enabled = enabled,
            onSelect = viewModel::setDrawsPerPath,
        )
        Spacer(Modifier.height(8.dp))
        SimChoiceRow(
            title = "重复路径",
            options = listOf(
                Choice(200, "200"),
                Choice(1_000, "1,000"),
                Choice(5_000, "5,000"),
            ),
            selected = ui.paths,
            enabled = enabled,
            onSelect = viewModel::setPaths,
        )
        Spacer(Modifier.height(8.dp))
        SimChoiceRow(
            title = "总预算",
            options = listOf(
                Choice<Long?>(null, "不设"),
                Choice<Long?>(2_000, "2 千"),
                Choice<Long?>(10_000, "1 万"),
                Choice<Long?>(50_000, "5 万"),
            ),
            selected = ui.budgetYuan,
            enabled = enabled,
            onSelect = viewModel::setBudget,
        )
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "种子 ${ui.seed}",
                style = MaterialTheme.typography.labelSmall,
                color = SketchPalette.InkSoft,
                modifier = Modifier.weight(1f),
            )
            SketchMiniButton(
                label = "换一批",
                seed = "sim-seed",
                onClick = viewModel::rerollSeed,
                enabled = enabled,
                modifier = Modifier,
            )
        }

        Spacer(Modifier.height(8.dp))
        val betsPerDraw = when (ui.strategy) {
            SimLabStrategyKind.RANDOM_SINGLE -> 1L
            SimLabStrategyKind.MY_DRAFT -> ui.draftBetCount
        }
        val totalBetDraws = ui.drawEvaluations * betsPerDraw
        val costPerDraw = betsPerDraw * 2
        Text(
            "对 ${formatInt(ui.drawEvaluations)} 次开奖判定 · 约 ${formatInt(totalBetDraws)} 注·期" +
                if (betsPerDraw > 0) " · 每期 ${formatInt(costPerDraw)} 元" else "",
            style = MaterialTheme.typography.bodySmall,
            color = SketchPalette.InkSoft,
        )
        if (ui.scaleTooLarge) {
            Text(
                "规模过大：请把“重复路径”或“周期”降一档（当前买法上限 ${formatInt(ui.maxDrawEvaluations)} 次判定）。",
                style = MaterialTheme.typography.bodySmall,
                color = SketchPalette.RedInk,
            )
        }
        ui.error?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = SketchPalette.RedInk)
        }
        Spacer(Modifier.height(10.dp))

        if (ui.running) {
            Text(
                "计算中 ${(ui.progress * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = SketchPalette.InkSoft,
            )
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(SketchPalette.InkFaint.copy(alpha = 0.35f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(ui.progress.coerceIn(0f, 1f))
                        .height(5.dp)
                        .background(SketchPalette.Ink),
                )
            }
            Spacer(Modifier.height(10.dp))
            SketchButton(
                label = "取消",
                onClick = viewModel::cancel,
                filled = false,
                seed = "sim-cancel",
                tilt = 0.3f,
            )
        } else {
            SketchButton(
                label = "开始模拟",
                onClick = viewModel::run,
                enabled = !ui.scaleTooLarge,
                seed = "sim-run",
                tilt = -0.3f,
            )
        }
    }
}

private data class Choice<T>(val value: T, val label: String)

@Composable
private fun <T> SimChoiceRow(
    title: String,
    options: List<Choice<T>>,
    selected: T,
    enabled: Boolean,
    optionEnabled: (T) -> Boolean = { true },
    onSelect: (T) -> Unit,
) {
    Text(title, style = MaterialTheme.typography.labelMedium, color = SketchPalette.InkSoft)
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEach { choice ->
            val isSelected = choice.value == selected
            val canClick = enabled && optionEnabled(choice.value)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .rotate(if (isSelected) -0.8f else 0.6f)
                    .sketchBox(
                        seed = "sim-$title-${choice.label}",
                        filled = isSelected,
                        enabled = canClick,
                        corner = 9.dp,
                    )
                    .selectable(
                        selected = isSelected,
                        enabled = canClick,
                        role = Role.RadioButton,
                        onClick = { onSelect(choice.value) },
                    )
                    .padding(horizontal = 3.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    choice.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        isSelected -> SketchPalette.Paper
                        canClick -> SketchPalette.InkSoft
                        else -> SketchPalette.InkFaint
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(result: SimResult) {
    SketchCard(seed = "sim-summary", tilt = 0.2f) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SketchSectionTitle("实验结论", modifier = Modifier.weight(1f))
            Text(
                "${result.spec.source.label} · ${strategyLabel(result.spec.strategy)}",
                style = MaterialTheme.typography.labelSmall,
                color = SketchPalette.InkSoft,
            )
        }
        Text(
            "${result.spec.paths} 次 × ${result.spec.drawsPerPath} 期 · seed ${result.spec.seed}",
            style = MaterialTheme.typography.labelSmall,
            color = SketchPalette.InkFaint,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            conclusionOf(result),
            style = MaterialTheme.typography.bodyMedium,
            color = SketchPalette.Ink,
        )
        Spacer(Modifier.height(10.dp))
        SketchBarRow(
            label = "盈利路径占比",
            fraction = result.profitableShare.toFloat(),
            valueText = pct(result.profitableShare),
            seed = "sim-profit",
        )
        Spacer(Modifier.height(6.dp))
        SketchMetaRow("中位回报率", pct(result.roi.p50))
        SketchMetaRow("P5 / P95", "${pct(result.roi.p5)} / ${pct(result.roi.p95)}")
        SketchMetaRow("模拟总投入", "${formatInt(result.totalCostYuan)} 元")
        SketchMetaRow(
            "模拟总奖金",
            "${formatInt(result.totalPrizeYuan)} 元",
            valueColor = if (result.netYuan >= 0) SketchPalette.Ink else SketchPalette.RedInk,
        )
        SketchMetaRow(
            "模拟净收益",
            "${signedInt(result.netYuan)} 元",
            valueColor = if (result.netYuan >= 0) SketchPalette.Ink else SketchPalette.RedInk,
        )
        result.spec.budgetYuan?.let {
            SketchMetaRow("破产路径", pct(result.ruinShare))
            SketchMetaRow("中位可买期数", "${result.medianSurvivalDraws} 期")
        }
        SketchMetaRow(
            "最大回撤（中位）",
            "${formatInt(result.maxDrawdownYuan.p50.toLong())} 元 / ${pct(result.maxDrawdownPct.p50)}",
        )
        SketchMetaRow(
            "最长不中（中位 / 最坏）",
            "${result.medianLongestNoWin} / ${result.worstLongestNoWin} 期",
        )
    }
}

@Composable
private fun FanCard(result: SimResult) {
    SketchCard(seed = "sim-fan", tilt = -0.15f) {
        SketchSectionTitle("累计净收益")
        Spacer(Modifier.height(4.dp))
        Text(
            "阴影：P5–P95；深线：中位；虚线：回本线。",
            style = MaterialTheme.typography.bodySmall,
            color = SketchPalette.InkSoft,
        )
        Spacer(Modifier.height(8.dp))
        FanChart(result.fan)
    }
}

@Composable
private fun FanChart(fan: SimFan) {
    val all = fan.p5 + fan.p25 + fan.p50 + fan.p75 + fan.p95
    val high = maxOf(0f, all.max())
    val low = minOf(0f, all.min())
    val range = max(1f, high - low)
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "单路径高 ${formatInt(high.toLong())} 元",
                style = MaterialTheme.typography.labelSmall,
                color = SketchPalette.InkFaint,
            )
            Text(
                "单路径低 ${formatInt(low.toLong())} 元",
                style = MaterialTheme.typography.labelSmall,
                color = SketchPalette.InkFaint,
            )
        }
        Spacer(Modifier.height(2.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(170.dp),
        ) {
            val n = fan.xs.size
            if (n < 2) return@Canvas
            val pad = 4.dp.toPx()
            val left = pad
            val right = size.width - pad
            val top = pad
            val bottom = size.height - pad
            fun x(i: Int): Float = left + (right - left) * i / (n - 1).toFloat()
            fun y(v: Float): Float = bottom - (v - low) / range * (bottom - top)

            fun band(topValues: FloatArray, bottomValues: FloatArray, color: Color) {
                val path = Path()
                for (i in 0 until n) {
                    val px = x(i)
                    val py = y(topValues[i])
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                for (i in n - 1 downTo 0) path.lineTo(x(i), y(bottomValues[i]))
                path.close()
                drawPath(path, color)
            }

            band(fan.p95, fan.p5, SketchPalette.Ink.copy(alpha = 0.07f))
            band(fan.p75, fan.p25, SketchPalette.Ink.copy(alpha = 0.12f))

            val zeroY = y(0f)
            drawLine(
                color = SketchPalette.InkFaint,
                start = Offset(left, zeroY),
                end = Offset(right, zeroY),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )

            fun line(values: FloatArray, color: Color, width: Float) {
                for (i in 0 until n - 1) {
                    drawLine(
                        color = color,
                        start = Offset(x(i), y(values[i])),
                        end = Offset(x(i + 1), y(values[i + 1])),
                        strokeWidth = width,
                        cap = StrokeCap.Round,
                    )
                }
            }
            line(fan.p5, SketchPalette.InkFaint, 1f)
            line(fan.p95, SketchPalette.InkFaint, 1f)
            line(fan.p25, SketchPalette.InkSoft, 1.2f)
            line(fan.p75, SketchPalette.InkSoft, 1.2f)
            line(fan.p50, SketchPalette.Ink, 2.2f)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0 期", style = MaterialTheme.typography.labelSmall, color = SketchPalette.InkFaint)
            Text(
                "${fan.xs.last()} 期",
                style = MaterialTheme.typography.labelSmall,
                color = SketchPalette.InkFaint,
            )
        }
    }
}

@Composable
private fun TierCard(result: SimResult) {
    SketchCard(seed = "sim-tier", tilt = 0.15f) {
        SketchSectionTitle("奖级命中 vs 理论")
        Spacer(Modifier.height(4.dp))
        Text(
            "分母 ${formatInt(result.totalBetDraws)} 注·期；期望 = 注·期 × 单注概率。\n" +
                if (result.spec.source == SimSource.HISTORICAL) {
                    "历史重放取公告实付，缺失的浮动奖按 500 万 / 15 万估算。"
                } else {
                    "理论随机的一二等奖按 500 万 / 15 万估算。"
                },
            style = MaterialTheme.typography.bodySmall,
            color = SketchPalette.InkSoft,
        )
        Spacer(Modifier.height(6.dp))
        PrizeTier.entries.sortedByDescending { it.rank }.forEach { tier ->
            val stat = result.tierStats[tier]
            if (stat != null) {
                SketchMetaRow(
                    label = tier.label,
                    value = "命中 ${stat.hits} · 期望 ${expectedText(stat.expected)}",
                    valueColor = if (stat.hits > 0) SketchPalette.RedInk else SketchPalette.InkSoft,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "头奖组合空间 1/${formatInt(17_721_088L)}；样本不足时，命中 0 不等于概率为 0。",
            style = MaterialTheme.typography.labelSmall,
            color = SketchPalette.InkFaint,
        )
    }
}

@Composable
private fun HonestyCard() {
    SketchCard(seed = "sim-honesty", tilt = 0.25f) {
        SketchSectionTitle("怎么读")
        Spacer(Modifier.height(6.dp))
        Text(
            "• 中位回报率是“典型路径”，不是你的保证。\n" +
                "• 最好路径通常来自一次头奖，不代表策略可复制。\n" +
                "• 历史重放只重放过去，不改变未来独立开奖。\n" +
                "• 长期期望为负是数学；预算耗尽概率比“能中多少”更值得先看。",
            style = MaterialTheme.typography.bodySmall,
            color = SketchPalette.InkSoft,
        )
    }
}

@Composable
private fun PlaceholderCard() {
    SketchCard(seed = "sim-placeholder", tilt = 0.2f) {
        SketchSectionTitle("还没有结果")
        Spacer(Modifier.height(6.dp))
        Text(
            "选好参数后点“开始模拟”。结果会给出收益分布、回撤、预算耗尽概率和奖级命中对照。",
            style = MaterialTheme.typography.bodyMedium,
            color = SketchPalette.InkSoft,
        )
        Spacer(Modifier.height(6.dp))
        SketchDivider(seed = "sim-placeholder-divider")
        Spacer(Modifier.height(6.dp))
        Text(
            "模拟不会预测下一期，也不评价“号码好坏”。",
            style = MaterialTheme.typography.bodySmall,
            color = SketchPalette.InkFaint,
        )
    }
}

private fun strategyLabel(strategy: SimStrategy): String = when (strategy) {
    SimStrategy.RandomSingle -> "每期机选"
    is SimStrategy.FixedDraft -> "我的号码 ${strategy.draft.betCount} 注"
    is SimStrategy.RandomCompound -> "随机复式 ${strategy.redCount}+${strategy.blueCount}"
}

private fun conclusionOf(result: SimResult): String {
    val budget = result.spec.budgetYuan
    val budgetText = if (budget != null) {
        " 预算中位可买 ${result.medianSurvivalDraws} 期，${pct(result.ruinShare)} 的路径没买满。"
    } else {
        ""
    }
    return "中位回报率 ${pct(result.roi.p50)}，盈利路径 ${pct(result.profitableShare)}；" +
        "回报率 P5–P95 为 ${pct(result.roi.p5)}–${pct(result.roi.p95)}。$budgetText" +
        " 这是模拟，不是预测；策略不改变单注概率。"
}

private fun pct(value: Double): String = "%.0f%%".format(value * 100)

private fun formatInt(value: Long): String = "%,d".format(value)

private fun signedInt(value: Long): String = if (value > 0) "+${formatInt(value)}" else formatInt(value)

private fun expectedText(value: Double): String = when {
    value >= 100 -> "%,.0f".format(value)
    value >= 10 -> "%.1f".format(value)
    value >= 1 -> "%.2f".format(value)
    else -> "%.3f".format(value)
}
