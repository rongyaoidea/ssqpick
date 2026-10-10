package com.ssqpick.feature.data

import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val featureDataModule = module {
    viewModel { DataViewModel(drawRepository = get(), settingsStore = get()) }
}
