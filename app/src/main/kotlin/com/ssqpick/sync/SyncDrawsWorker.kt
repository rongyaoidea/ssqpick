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
import com.ssqpick.store.DrawRepository
import com.ssqpick.store.SyncResult
import java.util.concurrent.TimeUnit

/**
 * 开奖自动同步：每天一次，需要网络。
 *
 * 双色球每周二、四、日 21:15 开奖，每天扫一次必然覆盖。
 * 复用 [DrawRepository.syncFromOfficial] 的增量逻辑，和手点同步走同一条路，
 * 首次运行同样会一次性导入全部历史。
 */
class SyncDrawsWorker(
    appContext: Context,
    params: WorkerParameters,
    private val drawRepository: DrawRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = when (drawRepository.syncFromOfficial()) {
        is SyncResult.Success, SyncResult.AlreadyUpToDate -> Result.success()

        // 网络抖动类失败：指数退避重试 3 次，还不行就等明天那次
        is SyncResult.Failure -> if (runAttemptCount >= MAX_ATTEMPTS) Result.failure() else Result.retry()
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
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}

/**
 * 手工 WorkerFactory：只为传一个 DrawRepository，
 * 不值得为此多引入 koin-androidx-workmanager。
 */
class SsqWorkerFactory(
    private val drawRepository: DrawRepository,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = when (workerClassName) {
        SyncDrawsWorker::class.java.name -> SyncDrawsWorker(appContext, workerParameters, drawRepository)
        else -> null
    }
}
