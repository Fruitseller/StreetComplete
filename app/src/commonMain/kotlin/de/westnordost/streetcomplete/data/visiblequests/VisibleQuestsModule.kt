package de.westnordost.streetcomplete.data.visiblequests

import de.westnordost.streetcomplete.data.quest.VisibleQuestsSource
import org.koin.dsl.module

val visibleQuestsModule = module {
    factory { QuestTypeOrderDao(get()) }
    factory { VisibleEditTypeDao(get()) }

    single { VisibleQuestsSource(get(), get(), get(), get(), get(), get(), get()) }

    single<QuestTypeOrderSource> { get<QuestTypeOrderController>() }
    single { QuestTypeOrderController(get(), get(), get()) }

    single { TeamModeQuestFilter(get(), get()) }

    single<QuestsHiddenSource> { get<QuestsHiddenController>() }
    single { QuestsHiddenController(get(), get()) }

    single<VisibleEditTypeSource> { get<VisibleEditTypeController>() }
    single { VisibleEditTypeController(get(), get(), get()) }
}
