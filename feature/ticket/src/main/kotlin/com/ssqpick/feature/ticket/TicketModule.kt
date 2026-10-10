package com.ssqpick.feature.ticket

import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val ticketModule = module {
    viewModel {
        TicketViewModel(
            ticketRepository = get(),
            drawRepository = get(),
            purchaseRepository = get(),
        )
    }
}
