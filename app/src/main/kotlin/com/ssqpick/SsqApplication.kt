package com.ssqpick

import android.app.Application
import androidx.work.Configuration
import com.ssqpick.feature.check.checkModule
import com.ssqpick.feature.machine.machineModule
import com.ssqpick.feature.ticket.ticketModule
import com.ssqpick.store.dataModule
import com.ssqpick.sync.SsqWorkerFactory
import com.ssqpick.sync.SyncDrawsWorker
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class SsqApplication :
    Application(),
    Configuration.Provider {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.ERROR else Level.NONE)
            androidContext(this@SsqApplication)
            modules(dataModule, machineModule, ticketModule, checkModule)
        }
        SyncDrawsWorker.schedule(this)
    }

    /**
     * WorkManager 手动初始化：自启动已在 Manifest 里关掉——
     * 它跑在 Application.onCreate 之前，那时 Koin 还没起来，
     * Factory 会拿不到 DrawRepository。这里保证 Worker 用的是 Koin 单例。
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(SsqWorkerFactory(GlobalContext.get().get()))
            .build()
}
