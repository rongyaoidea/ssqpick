package com.ssqpick.feature.data

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssqpick.designsystem.component.NumberBall
import com.ssqpick.designsystem.sketch.SketchBarRow
import com.ssqpick.designsystem.sketch.SketchButton
import com.ssqpick.designsystem.sketch.SketchCard
import com.ssqpick.designsystem.sketch.SketchChip
import com.ssqpick.designsystem.sketch.SketchLineChart
import com.ssqpick.designsystem.sketch.SketchMetaRow
import com.ssqpick.designsystem.sketch.SketchMiniButton
import com.ssqpick.designsystem.sketch.SketchPalette
import com.ssqpick.designsystem.sketch.SketchSectionTitle
import com.ssqpick.designsystem.theme.SsqDimens
import com.ssqpick.model.Draw
import com.ssqpick.model.PrizeTier
import com.ssqpick.rules.CoOccurrenceReport
import com.ssqpick.rules.DrawSchedule
import com.ssqpick.rules.FollowReport
import com.ssqpick.rules.MonteCarloReport
import com.ssqpick.rules.NumberStats
import com.ssqpick.rules.PairStat
import com.ssqpick.rules.PeriodMatch
import com.ssqpick.rules.ShapeStats
import com.ssqpick.rules.onThisDayOf
import com.ssqpick.rules.samePeriodOf
import kotlinx.coroutines.delay
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * 数据页：开奖日程、和值走势、冷热参考、开奖形态、模拟实验室。
 *
 * 这一页只描述历史与数学，不含任何"预测"。每张卡底部都带一句边界说明。
 */
@Composable
fun DataScreen(
    viewModel: DataViewModel,
    modifier: Modifier = Modifier,
) {
    val newest by viewModel.newest.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val shape by viewModel.shape.collectAsStateWithLifecycle()
    val trend by viewModel.trend.collectAsStateWithLifecycle()
    val sim by viewModel.sim.collectAsStateWithLifecycle()
    val simming by viewModel.simming.collectAsStateWithLifecycle()
    val drawList by viewModel.drawList.collectAsStateWithLifecycle()
    val notifyOn by viewModel.notifyOn.collectAsStateWithLifecycle()
    val cooc by viewModel.coOccurrence.collectAsStateWithLifecycle()
    val follow by viewModel.follow.collectAsStateWithLifecycle()
    val followTarget by viewModel.followTarget.collectAsStateWithLifecycle()

    // 开奖通知是订阅制：先把权限和系统开关问清楚，再写订阅状态。
    // 旧写法是先 toggle 再申请权限——用户拒绝后开关停在"已开启"，实际永远收不到。
    val context = LocalContext.current
    var pendingEnable by remember { mutableStateOf(false) }
    var notifyHint by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!pendingEnable) return@rememberLauncherForActivityResult
        pendingEnable = false
        if (granted && NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            viewModel.setDrawNotify(true)
        } else {
            viewModel.setDrawNotify(false)
            notifyHint = if (granted) {
                "系统里的通知被关掉了，订阅未开启"
            } else {
                "没有通知权限，订阅未开启"
            }
        }
    }
    val onToggleNotify = {
        notifyHint = null
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        when {
            notifyOn -> viewModel.setDrawNotify(false)

            needsPermission -> {
                pendingEnable = true
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }

            !NotificationManagerCompat.from(context).areNotificationsEnabled() ->
                notifyHint = "系统里的通知被关掉了，请先在系统设置里打开"

            else -> viewModel.setDrawNotify(true)
        }
    }

    var expandedCode by rememberSaveable { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SketchPalette.Canvas),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 14.dp)) {
                Spacer(Modifier.height(6.dp))
                NextDrawCard(
                    newest = newest,
                    notifyOn = notifyOn,
                    notifyHint = notifyHint,
                    onToggleNotify = onToggleNotify,
                )
                Spacer(Modifier.height(14.dp))
            }
        }
        item {
            Column(Modifier.padding(horizontal = 14.dp)) {
                TrendCard(trend)
                Spacer(Modifier.height(14.dp))
            }
        }
        item {
            Column(Modifier.padding(horizontal = 14.dp)) {
                StatsCard(stats)
                Spacer(Modifier.height(14.dp))
            }
        }
        item {
            Column(Modifier.padding(horizontal = 14.dp)) {
                CoOccurrenceCard(cooc)
                Spacer(Modifier.height(14.dp))
            }
        }
        item {
            Column(Modifier.padding(horizontal = 14.dp)) {
                FollowCard(
                    report = follow,
                    target = followTarget,
                    onSelect = viewModel::selectFollowTarget,
                )
                Spacer(Modifier.height(14.dp))
            }
        }
        item {
            Column(Modifier.padding(horizontal = 14.dp)) {
                ShapeCard(shape)
                Spacer(Modifier.height(14.dp))
            }
        }
        item {
            Column(Modifier.padding(horizontal = 14.dp)) {
                SimCard(
                    sim = sim,
                    running = simming,
                    onRerun = { viewModel.runSimulation() },
                )
                Spacer(Modifier.height(14.dp))
                SketchCard(seed = "data-draws-head", tilt = 0.25f) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SketchSectionTitle("往期开奖")
                        SketchChip(label = "${drawList.size} 期", seed = "draws-count", fill = null)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "点任意一期看奖级、销售额与奖池详情。",
                        style = MaterialTheme.typography.bodySmall,
                        color = SketchPalette.InkSoft,
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        }
        items(drawList, key = { it.code }) { draw ->
            DrawRow(
                draw = draw,
                allDraws = drawList,
                expanded = draw.code == expandedCode,
                onToggle = {
                    expandedCode = if (expandedCode == draw.code) null else draw.code
                },
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** 单期开奖行：点开看详情。为 2000+ 行性能，刻意不用手绘卡片（每行一次抖动路径没意义）。 */
@Composable
private fun DrawRow(
    draw: Draw,
    allDraws: List<Draw>,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "第 ${draw.code} 期 · ${draw.date}",
                style = MaterialTheme.typography.labelSmall,
                color = SketchPalette.InkSoft,
            )
            Text(
                if (expanded) "收起" else "详情",
                style = MaterialTheme.typography.labelSmall,
                color = SketchPalette.InkFaint,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            draw.red.forEach { NumberBall(it, isBlue = false, size = SsqDimens.ballSizeSmall) }
            Text("+", style = MaterialTheme.typography.labelSmall, color = SketchPalette.InkSoft)
            NumberBall(draw.blue, isBlue = true, size = SsqDimens.ballSizeSmall)
        }
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            if (draw.prizeGrades.isEmpty()) {
                Text(
                    "本地无该期奖级明细。",
                    style = MaterialTheme.typography.bodySmall,
                    color = SketchPalette.InkSoft,
                )
            } else {
                draw.prizeGrades.forEach { g ->
                    val amount = if (g.amountPerTicket > 0) formatYuan(g.amountPerTicket) else "待公告"
                    SketchMetaRow(
                        label = "${g.tier.label}单注",
                        value = amount,
                        valueColor = if (g.amountPerTicket > 0) SketchPalette.RedInk else SketchPalette.Ink,
                    )
                }
                Text(
                    "三至六等奖为固定奖金（3000 / 200 / 10 / 5 元）。",
                    style = MaterialTheme.typography.labelSmall,
                    color = SketchPalette.InkFaint,
                )
            }
            Spacer(Modifier.height(4.dp))
            SketchMetaRow(label = "销售额", value = if (draw.sales > 0) formatYuan(draw.sales) else "—")
            SketchMetaRow(label = "奖池", value = if (draw.poolAmount > 0) formatYuan(draw.poolAmount) else "—")

            // 历史对照：历年同期 + 往年的今天（数据变了会跟着重算）
            val periodMatches = remember(draw.code, allDraws) { samePeriodOf(allDraws, draw) }
            val dayMatches = remember(draw.code, allDraws) { onThisDayOf(allDraws, draw) }
            Spacer(Modifier.height(8.dp))
            Text(
                "历年同期（第 ${draw.code.drop(4)} 期）",
                style = MaterialTheme.typography.labelSmall,
                color = SketchPalette.InkSoft,
            )
            Spacer(Modifier.height(2.dp))
            if (periodMatches.isEmpty()) {
                Text(
                    "历年无同期。",
                    style = MaterialTheme.typography.labelSmall,
                    color = SketchPalette.InkFaint,
                )
            } else {
                periodMatches.forEach { m -> MatchLine(m, label = m.draw.code) }
            }
            if (dayMatches.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "往年的今天（%d月%d日）".format(draw.date.monthNumber, draw.date.dayOfMonth),
                    style = MaterialTheme.typography.labelSmall,
                    color = SketchPalette.InkSoft,
                )
                Spacer(Modifier.height(2.dp))
                dayMatches.forEach { m -> MatchLine(m, label = m.draw.date.toString()) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(SketchPalette.InkFaint.copy(alpha = 0.25f)),
        )
    }
}

/** 金额口语化：亿 / 万 / 元。 */
private fun formatYuan(yuan: Long): String = when {
    yuan >= 100_000_000 -> "%.2f 亿".format(yuan / 100_000_000.0)
    yuan >= 10_000 -> "%.0f 万".format(yuan / 10_000.0)
    else -> "$yuan 元"
}

/** 下期开奖倒计时。历法规则来自 DrawSchedule，休市顺延以官方为准。 */
@Composable
private fun NextDrawCard(
    newest: Draw?,
    notifyOn: Boolean,
    notifyHint: String?,
    onToggleNotify: () -> Unit,
) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val tz = TimeZone.currentSystemDefault()
    val now = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(tz)
    val next = DrawSchedule.nextDrawAfter(now)
    val remainMs = next.toInstant(tz).toEpochMilliseconds() - nowMs

    SketchCard(seed = "data-next", tilt = -0.3f) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SketchSectionTitle("下期开奖")
            SketchChip(label = "周二/四/日", seed = "next-days", fill = null)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "预计 %d月%d日 %s 21:15".format(
                next.monthNumber,
                next.dayOfMonth,
                weekdayZh(next.dayOfWeek),
            ),
            style = MaterialTheme.typography.titleMedium,
            color = SketchPalette.Ink,
        )
        Text(
            "还有 ${countdownText(remainMs)}",
            style = MaterialTheme.typography.bodyMedium,
            color = SketchPalette.Ink,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            newest?.let { "最新一期：第 ${it.code} 期 · ${it.date}" } ?: "本地尚无开奖数据",
            style = MaterialTheme.typography.bodySmall,
            color = SketchPalette.InkSoft,
        )
        Text(
            "休市期间顺延，以官方公告为准。",
            style = MaterialTheme.typography.bodySmall,
            color = SketchPalette.InkSoft,
        )
        Spacer(Modifier.height(10.dp))
        SketchMiniButton(
            label = if (notifyOn) "开奖通知：已开启" else "开奖通知：关闭中（点开启）",
            seed = "draw-notify-toggle",
            onClick = onToggleNotify,
            filled = notifyOn,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "开启后，每天后台同步到新一期开奖时通知一次。",
            style = MaterialTheme.typography.labelSmall,
            color = SketchPalette.InkFaint,
        )
        notifyHint?.let {
            Spacer(Modifier.height(2.dp))
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = SketchPalette.RedInk,
            )
        }
    }
}

@Composable
private fun TrendCard(trend: TrendData?) {
    SketchCard(seed = "data-trend", tilt = 0.25f) {
        SketchSectionTitle("和值走势")
        Spacer(Modifier.height(8.dp))
        if (trend == null || trend.sums.isEmpty()) {
            Text(
                "同步开奖数据后，这里画最近 100 期的红球和值。",
                style = MaterialTheme.typography.bodyMedium,
                color = SketchPalette.InkSoft,
            )
            return@SketchCard
        }
        Text(
            "最新 ${trend.sums.size} 期 · 均值 %.0f · 范围 %.0f ~ %.0f".format(
                trend.mean,
                trend.min,
                trend.max,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = SketchPalette.InkSoft,
        )
        SketchLineChart(
            values = trend.sums,
            baseline = trend.mean,
            seed = "sum-trend",
            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
        )
        Text(
            "横轴为时间（旧→新），细线是均值。和值只是描述，不预示下期。",
            style = MaterialTheme.typography.bodySmall,
            color = SketchPalette.InkSoft,
        )
    }
}

/** 冷热参考（从选号屏搬来，去掉草稿相关部分，此处纯历史）。 */
@Composable
private fun StatsCard(stats: NumberStats?) {
    SketchCard(seed = "data-stats", tilt = -0.25f) {
        SketchSectionTitle("冷热参考")
        Spacer(Modifier.height(8.dp))
        if (stats == null || stats.totalDraws == 0) {
            Text(
                "同步开奖数据后，这里会统计号码冷热。",
                style = MaterialTheme.typography.bodyMedium,
                color = SketchPalette.InkSoft,
            )
        } else {
            StatGroup(
                label = "热门红球",
                hint = "近 ${stats.totalDraws} 期出现最多",
                numbers = stats.hotReds(5),
                isBlue = false,
                counts = stats.redFrequency,
                unit = "次",
                extra = { n -> "%.1f%%".format((stats.redFrequency[n] ?: 0) * 100.0 / stats.totalDraws) },
            )
            Spacer(Modifier.height(8.dp))
            StatGroup(
                label = "久未出红球",
                hint = "遗漏期数最长",
                numbers = stats.coldReds(5),
                isBlue = false,
                counts = stats.redOmission,
                unit = "期",
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    StatGroup(
                        label = "热门蓝球",
                        hint = null,
                        numbers = stats.hotBlues(3),
                        isBlue = true,
                        counts = stats.blueFrequency,
                        unit = "次",
                        extra = { n -> "%.1f%%".format((stats.blueFrequency[n] ?: 0) * 100.0 / stats.totalDraws) },
                    )
                }
                Column(Modifier.weight(1f)) {
                    StatGroup(
                        label = "久未出蓝球",
                        hint = null,
                        numbers = stats.coldBlues(3),
                        isBlue = true,
                        counts = stats.blueOmission,
                        unit = "期",
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "红球理论出场率 18.2% · 蓝球 6.3%。冷热只是历史统计，不提高中奖率。",
                style = MaterialTheme.typography.bodySmall,
                color = SketchPalette.InkSoft,
            )
        }
    }
}

@Composable
private fun StatGroup(
    label: String,
    hint: String?,
    numbers: List<Int>,
    isBlue: Boolean,
    counts: Map<Int, Int>,
    unit: String,
    /** 号码下的第二行小字：热门组传出场率，遗漏组不传。 */
    extra: ((Int) -> String)? = null,
) {
    Text(
        if (hint == null) label else "$label · $hint",
        style = MaterialTheme.typography.labelSmall,
        color = SketchPalette.InkSoft,
    )
    Spacer(Modifier.height(5.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        numbers.forEach { n ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                NumberBall(n, isBlue = isBlue, size = SsqDimens.ballSizeSmall)
                Text(
                    "${counts[n] ?: 0}$unit",
                    style = MaterialTheme.typography.labelSmall,
                    color = SketchPalette.InkSoft,
                )
                extra?.let {
                    Text(
                        it(n),
                        style = MaterialTheme.typography.labelSmall,
                        color = SketchPalette.InkFaint,
                    )
                }
            }
        }
    }
}

/** 开奖形态（从选号屏搬来；草稿对照留在选号屏，这里纯历史）。 */
@Composable
private fun ShapeCard(shape: ShapeStats?) {
    SketchCard(seed = "data-shape", tilt = 0.3f) {
        SketchSectionTitle("开奖形态")
        Spacer(Modifier.height(8.dp))
        if (shape == null || shape.draws == 0) {
            Text(
                "同步开奖数据后，这里描述历史开奖的形态。",
                style = MaterialTheme.typography.bodyMedium,
                color = SketchPalette.InkSoft,
            )
            return@SketchCard
        }
        SketchMetaRow(
            label = "和值",
            value = "均值 %.0f · 九成在 %d~%d".format(shape.sumMean, shape.sumLow, shape.sumHigh),
        )
        SketchMetaRow(label = "奇数", value = "平均 %.1f 个/期".format(shape.oddMean))
        SketchMetaRow(
            label = "三区占比",
            value = shape.zoneShare.joinToString(" / ") { "%.0f%%".format(it * 100) },
        )
        SketchMetaRow(label = "含连号", value = "%.0f%% 的期次".format(shape.consecutiveShare * 100))
        Spacer(Modifier.height(6.dp))
        Text(
            "形态描述历史，不改变每期概率。",
            style = MaterialTheme.typography.bodySmall,
            color = SketchPalette.InkSoft,
        )
    }
}

/** 模拟实验室：随机买 N 期 × M 次，展示回报率如何收敛到负期望。 */
@Composable
private fun SimCard(
    sim: MonteCarloReport?,
    running: Boolean,
    onRerun: () -> Unit,
) {
    SketchCard(seed = "data-sim", tilt = -0.2f) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SketchSectionTitle("模拟实验室")
            sim?.let {
                SketchChip(label = "${it.drawsPerPath} 期 × ${it.paths} 次", seed = "sim-size")
            }
        }
        Spacer(Modifier.height(8.dp))
        if (sim == null) {
            Text(
                "正在计算…",
                style = MaterialTheme.typography.bodyMedium,
                color = SketchPalette.InkSoft,
            )
            return@SketchCard
        }
        Text(
            "每期随机买 1 注，连买 ${sim.drawsPerPath} 期，重复 ${sim.paths} 次。" +
                "（每期重新随机与守号在分布上完全等价。）",
            style = MaterialTheme.typography.bodySmall,
            color = SketchPalette.InkSoft,
        )
        Spacer(Modifier.height(8.dp))
        SketchBarRow(
            label = "盈利路径占比",
            fraction = sim.profitableShare.toFloat(),
            valueText = "%.1f%%".format(sim.profitableShare * 100),
            seed = "sim-profit",
        )
        Spacer(Modifier.height(8.dp))
        SketchMetaRow(label = "中位回报率", value = "%.0f%%".format(sim.medianRoi * 100))
        SketchMetaRow(
            label = "最好 / 最差路径",
            value = "%.0f%% / %.0f%%".format(sim.bestRoi * 100, sim.worstRoi * 100),
        )
        SketchMetaRow(label = "全部投入", value = "%,d 元".format(sim.totalCostYuan))
        SketchMetaRow(
            label = "全部奖金",
            value = "%,d 元".format(sim.totalPrizeYuan),
            valueColor = if (sim.totalPrizeYuan >= sim.totalCostYuan) SketchPalette.Ink else SketchPalette.RedInk,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            PrizeTier.entries.sortedByDescending { it.rank }
                .joinToString(" · ") { "${it.label} ${sim.tierHits[it] ?: 0}" },
            style = MaterialTheme.typography.bodySmall,
            color = SketchPalette.Ink,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "一二等奖按 500 万 / 15 万估算（浮动奖）。长期回报率为负是数学。",
            style = MaterialTheme.typography.bodySmall,
            color = SketchPalette.InkSoft,
        )
        Spacer(Modifier.height(10.dp))
        SketchButton(
            label = if (running) "计算中…" else "再模拟一次",
            onClick = onRerun,
            enabled = !running,
            filled = false,
            seed = "sim-rerun",
            tilt = 0.4f,
        )
    }
}

/** 同出/相克：全库红球两两共现，顶部与底部各取若干对，配理论期望说明。 */
@Composable
private fun CoOccurrenceCard(report: CoOccurrenceReport?) {
    SketchCard(seed = "data-cooc", tilt = -0.25f) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SketchSectionTitle("同出与相克")
            report?.let { SketchChip(label = "全库 ${it.draws} 期", seed = "cooc-draws", fill = null) }
        }
        Spacer(Modifier.height(8.dp))
        if (report == null || report.draws == 0) {
            Text(
                "同步开奖数据后统计红球两两共现。",
                style = MaterialTheme.typography.bodyMedium,
                color = SketchPalette.InkSoft,
            )
            return@SketchCard
        }
        Text("常一起出", style = MaterialTheme.typography.labelSmall, color = SketchPalette.InkSoft)
        Spacer(Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            report.topPairs.forEach { PairChip(it) }
        }
        Spacer(Modifier.height(8.dp))
        Text("少见同出", style = MaterialTheme.typography.labelSmall, color = SketchPalette.InkSoft)
        Spacer(Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            report.bottomPairs.forEach { PairChip(it) }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "每对理论期望 ≈%.0f 次（%d 期）——高低都只是随机涨落，同出/相克是描述，不是关系。"
                .format(report.expectedPerPair, report.draws),
            style = MaterialTheme.typography.bodySmall,
            color = SketchPalette.InkSoft,
        )
    }
}

@Composable
private fun PairChip(stat: PairStat) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        NumberBall(stat.a, isBlue = false, size = 24.dp)
        NumberBall(stat.b, isBlue = false, size = 24.dp)
        Text("${stat.count}次", style = MaterialTheme.typography.labelSmall, color = SketchPalette.InkSoft)
    }
}

/** 历史跟随：选一个号，看它出现后的下一期最常出什么。 */
@Composable
private fun FollowCard(
    report: FollowReport?,
    target: FollowTarget,
    onSelect: (Int, Boolean) -> Unit,
) {
    SketchCard(seed = "data-follow", tilt = 0.25f) {
        SketchSectionTitle("历史跟随")
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SketchMiniButton(
                label = "红球",
                seed = "follow-mode-red",
                onClick = { onSelect(target.number.coerceIn(1, 33), false) },
                filled = !target.isBlue,
                modifier = Modifier.weight(1f),
            )
            SketchMiniButton(
                label = "蓝球",
                seed = "follow-mode-blue",
                onClick = { onSelect(target.number.coerceIn(1, 16), true) },
                filled = target.isBlue,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val count = if (target.isBlue) 16 else 33
            (1..count).forEach { n ->
                SketchMiniButton(
                    label = "%02d".format(n),
                    seed = "follow-n-$n",
                    onClick = { onSelect(n, target.isBlue) },
                    filled = n == target.number,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (report == null || report.appearances == 0) {
            Text(
                "同步开奖数据后统计（该号出现后至少要有下一期）。",
                style = MaterialTheme.typography.bodyMedium,
                color = SketchPalette.InkSoft,
            )
            return@SketchCard
        }
        Text(
            "%02d 开出后的下一期（共 %d 次），最常出现：".format(target.number, report.appearances),
            style = MaterialTheme.typography.bodySmall,
            color = SketchPalette.InkSoft,
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            report.followers.forEach { f ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    NumberBall(f.number, isBlue = target.isBlue, size = 24.dp)
                    Text("${f.count}次", style = MaterialTheme.typography.labelSmall, color = SketchPalette.InkSoft)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "每个号理论期望 ≈%.1f 次——跟随是描述，没有“必跟号”。".format(report.expectedPerNumber),
            style = MaterialTheme.typography.bodySmall,
            color = SketchPalette.InkSoft,
        )
    }
}

/** 历史对照的一行：期号/日期 + 号码 + 重合信息。 */
@Composable
private fun MatchLine(m: PeriodMatch, label: String) {
    Text(
        buildString {
            append(label)
            append(" · ")
            append(m.draw.red.joinToString(" ") { "%02d".format(it) })
            append(" + %02d".format(m.draw.blue))
            append(" · 重合 ${m.redOverlap} 红")
            if (m.blueMatch) append("、蓝同")
        },
        style = MaterialTheme.typography.labelSmall,
        color = SketchPalette.Ink,
    )
}

private fun weekdayZh(d: DayOfWeek): String = when (d) {
    DayOfWeek.MONDAY -> "周一"
    DayOfWeek.TUESDAY -> "周二"
    DayOfWeek.WEDNESDAY -> "周三"
    DayOfWeek.THURSDAY -> "周四"
    DayOfWeek.FRIDAY -> "周五"
    DayOfWeek.SATURDAY -> "周六"
    DayOfWeek.SUNDAY -> "周日"
}

private fun countdownText(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val days = totalSec / 86_400
    val hours = totalSec % 86_400 / 3_600
    val minutes = totalSec % 3_600 / 60
    val seconds = totalSec % 60
    return when {
        days > 0 -> "$days 天 $hours 小时 $minutes 分 $seconds 秒"
        hours > 0 -> "$hours 小时 $minutes 分 $seconds 秒"
        else -> "$minutes 分 $seconds 秒"
    }
}
