package com.ssqpick.feature.ticket

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
import com.ssqpick.designsystem.sketch.SketchRangeMarker
import com.ssqpick.designsystem.sketch.SketchSectionTitle
import com.ssqpick.designsystem.sketch.sketchBox
import com.ssqpick.designsystem.theme.SsqDimens
import com.ssqpick.model.PrizeTier
import com.ssqpick.model.Ticket
import com.ssqpick.model.TicketKind
import com.ssqpick.rules.DraftBacktest
import com.ssqpick.rules.DraftFingerprint
import com.ssqpick.rules.HistoryRecord
import com.ssqpick.rules.NumberStats
import com.ssqpick.rules.PurchaseLedger
import com.ssqpick.rules.SmsCommand
import com.ssqpick.rules.TicketPurchaseSummary
import com.ssqpick.rules.TicketRules
import com.ssqpick.rules.TicketStatus
import com.ssqpick.rules.theoreticalBlueHitRate
import com.ssqpick.rules.theoreticalRedDistribution
import kotlinx.coroutines.delay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
/**
 * 已保存的号码：对奖页里的列表。
 *
 * 从"选号"页搬过来——对奖页本来就是"我的票 + 结果"，票行自带状态与期号，
 * 列表和核对结果是同一批数据的两种视图。默认只铺前 [collapsedCount] 张，
 * 展开后才全部渲染，长列表不会一次组合出几十行。
 */
@Composable
fun SavedTicketsSection(
    viewModel: TicketViewModel,
    modifier: Modifier = Modifier,
    collapsedCount: Int = 8,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val tickets by viewModel.tickets.collectAsStateWithLifecycle()
    val histories by viewModel.histories.collectAsStateWithLifecycle()
    val statuses by viewModel.statuses.collectAsStateWithLifecycle()
    val summaries by viewModel.purchaseSummaries.collectAsStateWithLifecycle()
    val periods by viewModel.ticketPeriods.collectAsStateWithLifecycle()

    // 第一次记录购买时顺手申请通知权限：后台自动对奖的中奖通知靠它
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val requestNotifyPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var renameTarget by remember { mutableStateOf<Ticket?>(null) }
    // 弹层打开时系统返回键先关弹层，而不是切导航/退出
    BackHandler(enabled = renameTarget != null) { renameTarget = null }

    // 存票筛选与展开
    var filterIdx by rememberSaveable { mutableIntStateOf(0) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val filter = TicketFilter.entries[filterIdx]
    val visibleTickets = tickets.filter { t ->
        when (filter) {
            TicketFilter.ALL -> true
            TicketFilter.PENDING -> statuses[t.id] == TicketStatus.NotDrawn
            TicketFilter.WON -> statuses[t.id] is TicketStatus.Won
            TicketFilter.BOUGHT -> (summaries[t.id]?.count ?: 0) > 0
        }
    }
    val shownTickets = if (expanded) visibleTickets else visibleTickets.take(collapsedCount)

    // —— 短信投注指令（00CP 体系）——
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    var smsFeedback by remember { mutableStateOf<SmsCopyFeedback?>(null) }
    var smsSettingsTarget by remember { mutableStateOf<SmsCopyTarget?>(null) }
    val selectedTickets = tickets.filter { it.id in ui.selectedCopyIds }

    // 纸条几秒后自动收起，不挡住列表
    LaunchedEffect(smsFeedback) {
        if (smsFeedback != null) {
            delay(6_000)
            smsFeedback = null
        }
    }

    fun copyToClipboard(command: String, target: SmsCopyTarget, recordTicketId: Long?) {
        clipboard.setPrimaryClip(ClipData.newPlainText("ssq-sms", command))
        smsFeedback = SmsCopyFeedback(
            title = "已复制 · 粘贴到短信发送",
            body = command,
            target = target,
            recordTicketId = recordTicketId,
        )
    }

    fun copySingle(ticket: Ticket) {
        if (ticket.draft.blueBold.isNotEmpty()) {
            smsFeedback = SmsCopyFeedback(
                title = "暂不支持",
                body = "双色球没有蓝胆拖玩法，这类票无法生成短信指令。",
            )
            return
        }
        val command = SmsCommand.of(ticket.draft) ?: return
        val noPurchase = (summaries[ticket.id]?.count ?: 0) == 0
        copyToClipboard(command, SmsCopyTarget.Single(ticket.id), if (noPurchase) ticket.id else null)
    }

    fun copyMulti() {
        val command = SmsCommand.multi(selectedTickets.map { it.draft }) ?: return
        copyToClipboard(command, SmsCopyTarget.Multi, recordTicketId = null)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SketchSectionTitle("已保存的号码")
            SketchChip(
                label = if (visibleTickets.size == tickets.size) {
                    "${tickets.size} 张"
                } else {
                    "${visibleTickets.size} / ${tickets.size} 张"
                },
                seed = "ticket-count",
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TicketFilter.entries.forEach { f ->
                SketchMiniButton(
                    label = f.label,
                    seed = "ticket-filter-${f.name}",
                    onClick = { filterIdx = f.ordinal },
                    filled = f == filter,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        ui.actionMessage?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = SketchPalette.Ink)
        }
        smsFeedback?.let { fb ->
            SmsCopyStrip(
                feedback = fb,
                onAdjust = fb.target?.let { t -> { smsSettingsTarget = t } },
                onRecord = fb.recordTicketId?.let { id ->
                    {
                        tickets.firstOrNull { it.id == id }?.let {
                            viewModel.recordPurchase(it)
                            requestNotifyPermission()
                        }
                        smsFeedback = null
                    }
                },
            )
        }
        if (selectedTickets.isNotEmpty()) {
            MultiCopyBar(
                count = selectedTickets.size,
                onCopy = { copyMulti() },
                onClear = viewModel::clearCopySelection,
            )
        }
        Spacer(Modifier.height(10.dp))
        when {
            tickets.isEmpty() -> SketchCard(seed = "ticket-empty", tilt = 0.3f) {
                Text(
                    "还没有保存的号码。摇号或手选后点“保存这注”。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SketchPalette.InkSoft,
                )
            }

            visibleTickets.isEmpty() -> SketchCard(seed = "ticket-filter-empty", tilt = 0.2f) {
                Text(
                    "没有符合“${filter.label}”的号码。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SketchPalette.InkSoft,
                )
            }

            else -> {
                shownTickets.forEach { ticket ->
                    TicketRow(
                        ticket = ticket,
                        history = histories[ticket.id],
                        status = statuses[ticket.id],
                        period = periods[ticket.id],
                        summary = summaries[ticket.id],
                        selectable = ticket.draft.kind == TicketKind.SIMPLE,
                        selected = ticket.id in ui.selectedCopyIds,
                        onToggleSelect = { viewModel.toggleCopySelection(ticket) },
                        onCopy = { copySingle(ticket) },
                        onRecord = {
                            viewModel.recordPurchase(ticket)
                            requestNotifyPermission()
                        },
                        onRename = { renameTarget = ticket },
                        onUndo = { viewModel.undoLastPurchase(ticket) },
                        onDelete = { viewModel.delete(ticket) },
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (visibleTickets.size > collapsedCount) {
                    SketchMiniButton(
                        label = if (expanded) "收起" else "展开全部（${visibleTickets.size} 张）",
                        seed = "ticket-expand",
                        onClick = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    smsSettingsTarget?.let { target ->
        val drafts = when (target) {
            is SmsCopyTarget.Single ->
                tickets.firstOrNull { it.id == target.ticketId }?.let { listOf(it.draft) }.orEmpty()

            SmsCopyTarget.Multi -> selectedTickets.map { it.draft }
        }
        if (drafts.isNotEmpty()) {
            SmsSettingsDialog(
                drafts = drafts,
                multi = target == SmsCopyTarget.Multi,
                onDismiss = { smsSettingsTarget = null },
                onCopy = { command ->
                    val recordId = if (target is SmsCopyTarget.Single &&
                        (summaries[target.ticketId]?.count ?: 0) == 0
                    ) {
                        target.ticketId
                    } else {
                        null
                    }
                    copyToClipboard(command, target, recordId)
                    smsSettingsTarget = null
                },
            )
        }
    }

    renameTarget?.let { target ->
        SketchInputDialog(
            title = "备注号码 #${target.id}",
            initial = target.label.orEmpty(),
            onDismiss = { renameTarget = null },
            onConfirm = { label ->
                viewModel.rename(target, label)
                renameTarget = null
            },
        )
    }
}

/** 存票相对最新一期的状态徽标：数据没到之前不占位。 */
@Composable
private fun StatusChip(status: TicketStatus?) {
    when (status) {
        null -> Unit

        TicketStatus.NotDrawn -> SketchChip(label = "未开奖", seed = "st-notdrawn", fill = null)

        TicketStatus.Missed -> SketchChip(label = "未中奖", seed = "st-missed", fill = null)

        is TicketStatus.Won -> SketchChip(
            // 一二等奖无公告时金额为 0：只报奖级，不编金额
            label = if (status.amountYuan > 0) {
                "${status.topTier?.label} · %,d 元".format(status.amountYuan)
            } else {
                status.topTier?.label ?: "中奖"
            },
            seed = "st-won-${status.topTier}",
        )
    }
}

@Composable
private fun TicketRow(
    ticket: Ticket,
    history: HistoryRecord?,
    status: TicketStatus?,
    /** 对应期号文案（"第 X 期"或"X月X日开奖"）。 */
    period: String?,
    summary: TicketPurchaseSummary?,
    /** 单式票才可勾选（单式多注复制）。 */
    selectable: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onCopy: () -> Unit,
    onRecord: () -> Unit,
    onRename: () -> Unit,
    onUndo: () -> Unit,
    onDelete: () -> Unit,
) {
    SketchCard(seed = "ticket-${ticket.id}", tilt = if (ticket.id % 2L == 0L) 0.25f else -0.25f) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectable) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clickable(role = Role.Checkbox, onClick = onToggleSelect)
                            .semantics(mergeDescendants = true) {
                                stateDescription = if (selected) "已勾选" else "未勾选"
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(22.dp)
                                .sketchBox(seed = "sel-${ticket.id}", filled = selected, corner = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Text(
                                    "✓",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SketchPalette.Paper,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    ticket.label ?: "号码 #${ticket.id}",
                    style = MaterialTheme.typography.titleMedium,
                    color = SketchPalette.Ink,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Spacer(Modifier.width(8.dp))
            StatusChip(status)
        }
        period?.let {
            Spacer(Modifier.height(2.dp))
            Text(
                "对应 $it",
                style = MaterialTheme.typography.labelSmall,
                color = SketchPalette.InkFaint,
            )
        }
        Spacer(Modifier.height(6.dp))
        // 存票可能是 20 红大复式：一行摆不下会顶出屏幕，换行显示
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ticket.draft.redPool.sorted().forEach {
                NumberBall(it, isBlue = false, size = SsqDimens.ballSizeSmall)
            }
        }
        Spacer(Modifier.height(2.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ticket.draft.bluePool.sorted().forEach {
                NumberBall(it, isBlue = true, size = SsqDimens.ballSizeSmall)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "${TicketRules.betCount(ticket.draft)} 注 · ${ticket.draft.kind.label}",
            style = MaterialTheme.typography.labelSmall,
            color = SketchPalette.InkSoft,
        )
        summary?.takeIf { it.count > 0 }?.let { s ->
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    append("已购 ${s.count} 笔")
                    if (s.pendingCount > 0) append("（待开奖 ${s.pendingCount}）")
                    append(" · 投入 ${s.costYuan} 元")
                    if (s.prizeYuan > 0) append(" · 中奖 ${s.prizeYuan} 元")
                },
                style = MaterialTheme.typography.labelSmall,
                color = SketchPalette.Ink,
            )
        }
        history?.let { h ->
            Spacer(Modifier.height(2.dp))
            Text(
                if (h.hasEverWon) {
                    "历史最佳：${h.highestTier?.label} · 第${h.highestTierDrawCode}期"
                } else {
                    "${h.checkedDraws} 期里未中过奖"
                },
                style = MaterialTheme.typography.labelSmall,
                color = SketchPalette.InkSoft,
            )
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SketchMiniButton(
                label = "复制指令",
                seed = "ticket-copy-${ticket.id}",
                onClick = onCopy,
            )
            SketchMiniButton(
                label = "记录购买",
                seed = "ticket-buy-${ticket.id}",
                onClick = onRecord,
                filled = true,
            )
            SketchMiniButton(label = "备注", seed = "ticket-label-${ticket.id}", onClick = onRename)
            if ((summary?.count ?: 0) > 0) {
                SketchMiniButton(label = "撤销购买", seed = "ticket-undo-${ticket.id}", onClick = onUndo)
            }
            SketchMiniButton(label = "删除", seed = "ticket-del-${ticket.id}", onClick = onDelete)
        }
    }
}

/** 备注编辑：用 Dialog 承载，才能从列表里弹出（不再假设自己在整屏 Box 里）。 */
@Composable
private fun SketchInputDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        SketchCard(seed = "rename-dialog", tilt = 0f) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = SketchPalette.Ink)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text("例如：守号-周三") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SketchMiniButton(
                    label = "取消",
                    seed = "dlg-cancel",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                SketchMiniButton(
                    label = "保存",
                    seed = "dlg-ok",
                    onClick = { onConfirm(text) },
                    filled = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 存票列表筛选。 */
private enum class TicketFilter(val label: String) {
    ALL("全部"),
    PENDING("未开奖"),
    WON("中奖"),
    BOUGHT("已购"),
}
