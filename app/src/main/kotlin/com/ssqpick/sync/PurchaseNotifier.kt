package com.ssqpick.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ssqpick.model.Draw

/** 一笔中奖事件（后台结算后发通知用）。 */
data class WinEvent(
    val drawCode: String,
    val tierLabel: String,
    val amountYuan: Long,
)

/**
 * 购票对奖与开奖结果通知。
 *
 * 对奖通知只在新中奖时发一次；开奖通知是订阅制（默认关，见 SettingsStore），
 * 后台拉到新一期且订阅开启时发。未授权通知时全部静默跳过。
 */
object PurchaseNotifier {

    const val CHANNEL_ID = "purchase-result"
    const val CHANNEL_DRAW_ID = "draw-result"
    private const val NOTIFICATION_ID = 1001
    private const val NOTIFICATION_DRAW_ID = 1002

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "购票对奖",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "你记录的购买中奖时通知一次"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DRAW_ID,
                "开奖结果",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "订阅后，每期开奖时通知一次"
            },
        )
    }

    fun notifyWins(context: Context, wins: List<WinEvent>) {
        if (wins.isEmpty()) return
        val total = wins.sumOf { it.amountYuan }
        val title = if (wins.size == 1) "你有一笔购票中奖" else "${wins.size} 笔购票中奖"
        val lines = wins.take(3).joinToString("；") { w ->
            if (w.amountYuan > 0) {
                "第${w.drawCode}期 ${w.tierLabel} ${w.amountYuan} 元"
            } else {
                "第${w.drawCode}期 ${w.tierLabel}"
            }
        }
        val text = if (total > 0) "$lines（合计 $total 元）" else lines
        notify(context, CHANNEL_ID, NOTIFICATION_ID, title, text)
    }

    /** 新一期开奖公告（订阅开启时由后台同步触发）。 */
    fun notifyNewDraw(context: Context, draw: Draw) {
        val reds = draw.red.joinToString(" ") { "%02d".format(it) }
        val text = "红 $reds · 蓝 %02d".format(draw.blue)
        notify(context, CHANNEL_DRAW_ID, NOTIFICATION_DRAW_ID, "第 ${draw.code} 期已开奖", text)
    }

    private fun notify(context: Context, channel: String, id: Int, title: String, text: String) {
        val compat = NotificationManagerCompat.from(context)
        if (!compat.areNotificationsEnabled()) return

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        try {
            compat.notify(id, notification)
        } catch (_: SecurityException) {
            // 权限在运行中被撤销：静默跳过，数据不受影响
        }
    }
}
