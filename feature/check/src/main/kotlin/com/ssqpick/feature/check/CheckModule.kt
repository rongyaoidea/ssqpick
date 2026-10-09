package com.ssqpick.feature.check

import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val checkModule = module {
    viewModel {
        CheckViewModel(
            ticketRepository = get(),
            drawRepository = get(),
        )
    }
}
