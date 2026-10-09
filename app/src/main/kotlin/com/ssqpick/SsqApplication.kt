package com.ssqpick

import android.app.Application
import com.ssqpick.store.dataModule
import com.ssqpick.feature.check.checkModule
import com.ssqpick.feature.machine.machineModule
import com.ssqpick.feature.ticket.ticketModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class SsqApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.ERROR else Level.NONE)
            androidContext(this@SsqApplication)
            modules(dataModule, machineModule, ticketModule, checkModule)
        }
    }
}
