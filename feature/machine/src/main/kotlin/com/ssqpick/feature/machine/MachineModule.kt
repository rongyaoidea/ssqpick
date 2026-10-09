package com.ssqpick.feature.machine

import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val machineModule = module {
    single { TicketHistoryProvider(drawRepository = get()) }

    viewModel {
        MachineViewModel(
            ticketRepository = get(),
            historyProvider = get(),
        )
    }
}
