package de.westnordost.streetcomplete.di

import de.westnordost.streetcomplete.data.allEditTypesModule
import de.westnordost.streetcomplete.data.download.downloadModule
import de.westnordost.streetcomplete.data.edithistory.editHistoryModule
import de.westnordost.streetcomplete.data.feedsModule
import de.westnordost.streetcomplete.data.logs.logsModule
import de.westnordost.streetcomplete.data.messages.messagesModule
import de.westnordost.streetcomplete.data.meta.metadataModule
import de.westnordost.streetcomplete.data.osm.created_elements.createdElementsModule
import de.westnordost.streetcomplete.data.osm.edits.elementEditsModule
import de.westnordost.streetcomplete.data.osm.geometry.elementGeometryModule
import de.westnordost.streetcomplete.data.osm.mapdata.mapDataModule
import de.westnordost.streetcomplete.data.osm.osmquests.osmQuestModule
import de.westnordost.streetcomplete.data.osmApiModule
import de.westnordost.streetcomplete.data.osmcal.calendarEventsModule
import de.westnordost.streetcomplete.data.osmnotes.edits.noteEditsModule
import de.westnordost.streetcomplete.data.osmnotes.notequests.osmNoteQuestModule
import de.westnordost.streetcomplete.data.osmnotes.notesModule
import de.westnordost.streetcomplete.data.overlays.overlayModule
import de.westnordost.streetcomplete.data.preferences.preferencesModule
import de.westnordost.streetcomplete.data.presets.editTypePresetsModule
import de.westnordost.streetcomplete.data.upload.uploadModule
import de.westnordost.streetcomplete.data.urlconfig.urlConfigModule
import de.westnordost.streetcomplete.data.user.achievements.achievementDefinitionsModule
import de.westnordost.streetcomplete.data.user.achievements.achievementsModule
import de.westnordost.streetcomplete.data.user.achievements.editTypeAliasesModule
import de.westnordost.streetcomplete.data.user.statistics.statisticsModule
import de.westnordost.streetcomplete.data.user.userModule
import de.westnordost.streetcomplete.data.visiblequests.visibleQuestsModule
import de.westnordost.streetcomplete.data.weeklyosm.weeklyOsmModule
import de.westnordost.streetcomplete.iosControllersModule
import de.westnordost.streetcomplete.iosModule
import de.westnordost.streetcomplete.overlays.overlaysModule
import de.westnordost.streetcomplete.quests.questsModule
import de.westnordost.streetcomplete.screens.about.aboutScreenModule
import de.westnordost.streetcomplete.screens.main.map.mapModule
import de.westnordost.streetcomplete.screens.settings.settingsScreenModule
import de.westnordost.streetcomplete.screens.user.userScreenModule
import de.westnordost.streetcomplete.ui.util.measure.arModule
import de.westnordost.streetcomplete.ui.util.photo.photoModule
import de.westnordost.streetcomplete.util.logs.IosLogger
import de.westnordost.streetcomplete.util.logs.Log
import org.koin.core.context.startKoin

private var koinStarted = false

/** Starts Koin for iOS exactly once. Safe to call from every MainViewController()
 *  invocation (Compose may recreate the hosting UIViewController).
 *
 *  This is the iOS counterpart of `StreetCompleteApplication.onCreate()`'s `startKoin`,
 *  minus the four androidMain-only modules (appModule, mainModule, questModule, androidModule),
 *  plus iosModule (platform infra) and iosControllersModule (no-op controller stubs). */
fun initKoin() {
    if (koinStarted) return
    koinStarted = true

    Log.instances.add(IosLogger())

    startKoin {
        modules(
            iosModule,
            iosControllersModule,
            achievementsModule,
            achievementDefinitionsModule,
            editTypeAliasesModule,
            aboutScreenModule,
            mapModule,
            userScreenModule,
            createdElementsModule,
            logsModule,
            downloadModule,
            editHistoryModule,
            elementEditsModule,
            elementGeometryModule,
            mapDataModule,
            metadataModule,
            noteEditsModule,
            notesModule,
            messagesModule,
            osmApiModule,
            osmNoteQuestModule,
            osmQuestModule,
            preferencesModule,
            editTypePresetsModule,
            visibleQuestsModule,
            allEditTypesModule,
            questsModule,
            settingsScreenModule,
            statisticsModule,
            uploadModule,
            userModule,
            arModule,
            photoModule,
            overlaysModule,
            overlayModule,
            urlConfigModule,
            weeklyOsmModule,
            calendarEventsModule,
            feedsModule,
        )
    }
}
