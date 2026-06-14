package de.westnordost.streetcomplete.data.quest

import org.koin.dsl.module

val questModule = module {
    single { QuestAutoSyncer(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
}
