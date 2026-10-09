package com.ssqpick.store

import com.ssqpick.store.db.DatabaseFactory
import com.ssqpick.store.network.CwlApi
import com.ssqpick.store.network.defaultClient
import org.koin.dsl.module

val dataModule = module {
    single { defaultClient() }
    single { CwlApi(get()) }

    single {
        val factory = DatabaseFactory(context = get(), api = get())
        factory.draws to factory.tickets
    }

    single<DrawRepository> { get<Pair<DrawRepository, TicketRepository>>().first }
    single<TicketRepository> { get<Pair<DrawRepository, TicketRepository>>().second }
}
