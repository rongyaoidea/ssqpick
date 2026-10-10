package com.ssqpick.store

import com.ssqpick.store.db.DatabaseFactory
import com.ssqpick.store.network.CwlApi
import com.ssqpick.store.network.defaultClient
import org.koin.dsl.module

val dataModule = module {
    single { defaultClient() }
    single { CwlApi(get()) }

    single { DatabaseFactory(context = get(), api = get()) }

    single<SettingsStorage> { SettingsStore(context = get()) }

    single<DrawRepository> { get<DatabaseFactory>().draws }
    single<TicketRepository> { get<DatabaseFactory>().tickets }
    single<PurchaseRepository> { get<DatabaseFactory>().purchases }
}
