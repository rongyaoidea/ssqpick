package com.ssqpick.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** 一笔中奖事件（后台结算后发通知用）。 */
data class WinEvent(
    val drawCode: String,
    val tierLabel: String,
    val amountYuan: Long,
)

/**
 * 购票对奖通知。
 *
 * 只在后台 Worker 结算出**新中奖**时发一次；用户没授权通知时静默跳过，
 * 不影响结算本身（数据该更新还得更新）。
 */
object PurchaseNotifier {

    const val CHANNEL_ID = "purchase-result"
    private const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "购票对奖",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "你记录的购买中奖时通知一次"
        }
        manager.createNotificationChannel(channel)
    }

    fun notifyWins(context: Context, wins: List<WinEvent>) {
        if (wins.isEmpty()) return
        val compat = NotificationManagerCompat.from(context)
        if (!compat.areNotificationsEnabled()) return

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

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        try {
            compat.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // 权限在运行中被撤销：静默跳过，结算数据不受影响
        }
    }
}
