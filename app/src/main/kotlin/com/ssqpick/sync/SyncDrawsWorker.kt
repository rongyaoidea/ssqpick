package com.ssqpick.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.ssqpick.rules.outcomeOf
import com.ssqpick.store.DrawRepository
import com.ssqpick.store.PurchaseRepository
import com.ssqpick.store.SettingsStore
import com.ssqpick.store.SyncResult
import com.ssqpick.store.TicketRepository
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * 开奖自动同步：每天一次，需要网络。
 *
 * 双色球每周二、四、日 21:15 开奖，每天扫一次必然覆盖。
 * 复用 [DrawRepository.syncFromOfficial] 的增量逻辑，和手点同步走同一条路，
 * 首次运行同样会一次性导入全部历史。
 *
 * 同步成功后做结算：把未结算的购买绑到新开的一期上，
 * 有中奖的发通知（同一条购买只结算一次，不会重复通知）。
 */
class SyncDrawsWorker(
    appContext: Context,
    params: WorkerParameters,
    private val drawRepository: DrawRepository,
    private val ticketRepository: TicketRepository,
    private val purchaseRepository: PurchaseRepository,
    private val settingsStore: SettingsStore,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = when (val r = drawRepository.syncFromOfficial()) {
        is SyncResult.Success -> {
            settlePurchases()
            if (r.imported > 0) notifyNewDraw()
            Result.success()
        }

        SyncResult.AlreadyUpToDate -> {
            // 没新数据也跑一遍：用户可能刚记录购买，而目标期已经在库里
            settlePurchases()
            Result.success()
        }

        // 网络抖动类失败：指数退避重试 3 次，还不行就等明天那次
        is SyncResult.Failure -> if (runAttemptCount >= MAX_ATTEMPTS) Result.failure() else Result.retry()
    }

    /** 订阅开启时，把最新一期开奖号通知出去。 */
    private suspend fun notifyNewDraw() {
        if (!settingsStore.drawResultNotify) return
        val newest = drawRepository.observeNewest().first() ?: return
        PurchaseNotifier.notifyNewDraw(applicationContext, newest)
    }

    /** 结算未处理的购买；新中奖发一次通知。整段包 runCatching：结算不能拖垮同步任务。 */
    private suspend fun settlePurchases() {
        runCatching {
            val pending = purchaseRepository.unsettled()
            if (pending.isEmpty()) return@runCatching
            val tickets = ticketRepository.all().associateBy { it.id }
            val drawsAsc = drawRepository.all().sortedBy { it.date }
            val wins = mutableListOf<WinEvent>()
            val settledIds = mutableListOf<Long>()
            for (p in pending) {
                val ticket = tickets[p.ticketId] ?: continue
                val outcome = outcomeOf(p, ticket, drawsAsc)
                val draw = outcome.draw ?: continue
                settledIds += p.id
                if (outcome.hasWon) {
                    wins += WinEvent(
                        drawCode = draw.code,
                        tierLabel = outcome.topTier?.label ?: "中奖",
                        amountYuan = outcome.amountYuan,
                    )
                }
            }
            purchaseRepository.settle(settledIds)
            if (wins.isNotEmpty()) PurchaseNotifier.notifyWins(applicationContext, wins)
        }
    }

    companion object {
        const val UNIQUE_NAME = "draw-sync"
        private const val MAX_ATTEMPTS = 3

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncDrawsWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                // UPDATE 而不是 KEEP：改了周期/约束后已装用户也能生效，
                // 同时保留既有排期（不会从今天重新计时）
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}

/**
 * 手工 WorkerFactory：为 Worker 传几个 repository，
 * 不值得为此多引入 koin-androidx-workmanager。
 */
class SsqWorkerFactory(
    private val drawRepository: DrawRepository,
    private val ticketRepository: TicketRepository,
    private val purchaseRepository: PurchaseRepository,
    private val settingsStore: SettingsStore,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = when (workerClassName) {
        SyncDrawsWorker::class.java.name -> SyncDrawsWorker(
            appContext,
            workerParameters,
            drawRepository,
            ticketRepository,
            purchaseRepository,
            settingsStore,
        )

        else -> null
    }
}
