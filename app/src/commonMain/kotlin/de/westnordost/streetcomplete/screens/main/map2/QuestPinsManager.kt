package de.westnordost.streetcomplete.screens.main.map2

import de.westnordost.streetcomplete.data.download.tiles.TilesRect
import de.westnordost.streetcomplete.data.download.tiles.enclosingTilesRect
import de.westnordost.streetcomplete.data.osm.mapdata.BoundingBox
import de.westnordost.streetcomplete.data.osm.mapdata.ElementType
import de.westnordost.streetcomplete.data.quest.OsmNoteQuestKey
import de.westnordost.streetcomplete.data.quest.OsmQuestKey
import de.westnordost.streetcomplete.data.quest.Quest
import de.westnordost.streetcomplete.data.quest.QuestKey
import de.westnordost.streetcomplete.data.quest.QuestType
import de.westnordost.streetcomplete.data.quest.QuestTypeRegistry
import de.westnordost.streetcomplete.data.quest.VisibleQuestsSource
import de.westnordost.streetcomplete.data.visiblequests.QuestTypeOrderSource
import de.westnordost.streetcomplete.screens.main.map2.layers.Pin
import de.westnordost.streetcomplete.util.math.contains
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** commonMain quest-pin producer (port of androidMain QuestPinsManager): listens to
 *  VisibleQuestsSource + a viewport bbox flow, windows by z16 tiles, fetches visible quests off
 *  the main thread, builds Pins (incl. OsmNoteQuest), and exposes them as a StateFlow. Imports
 *  ZERO maplibre-compose types. */
class QuestPinsManager(
    private val viewportBbox: Flow<BoundingBox?>,
    private val visibleQuestsSource: VisibleQuestsSource,
    private val questTypeRegistry: QuestTypeRegistry,
    private val questTypeOrderSource: QuestTypeOrderSource,
    private val scope: CoroutineScope,
) {
    private val _pins = MutableStateFlow<List<Pin>>(emptyList())
    val pins: StateFlow<List<Pin>> = _pins.asStateFlow()

    private val questTypeOrdersLock = ReentrantLock()
    private val questTypeOrders: MutableMap<QuestType, Int> = mutableMapOf()
    private var lastDisplayedRect: TilesRect? = null
    private val questsInView: MutableMap<QuestKey, List<Pin>> = mutableMapOf()
    private val questsInViewMutex = Mutex()
    private val visibleQuestsSourceMutex = Mutex()
    private var updateJob: Job? = null
    private var currentBbox: BoundingBox? = null

    private val visibleQuestsListener = object : VisibleQuestsSource.Listener {
        override fun onUpdated(added: Collection<Quest>, removed: Collection<QuestKey>) {
            val oldUpdateJob = updateJob
            updateJob = scope.launch {
                oldUpdateJob?.join() // don't cancel: updateQuestPins only updates existing data
                updateQuestPins(added, removed)
            }
        }
        override fun onInvalidated() = invalidate()
    }

    private val questTypeOrderListener = object : QuestTypeOrderSource.Listener {
        override fun onQuestTypeOrderAdded(item: QuestType, toAfter: QuestType) = reinitializeQuestTypeOrders()
        override fun onQuestTypeOrdersChanged() = reinitializeQuestTypeOrders()
    }

    fun start() {
        initializeQuestTypeOrders()
        visibleQuestsSource.addListener(visibleQuestsListener)
        questTypeOrderSource.addListener(questTypeOrderListener)
        scope.launch {
            viewportBbox.collect { bbox ->
                currentBbox = bbox
                updateCurrentScreenArea(bbox)
            }
        }
    }

    fun stop() {
        visibleQuestsSource.removeListener(visibleQuestsListener)
        questTypeOrderSource.removeListener(questTypeOrderListener)
    }

    fun getQuestKey(properties: Map<String, String>): QuestKey? = properties.toQuestKey()

    private fun invalidate() {
        lastDisplayedRect = null
        scope.launch { updateCurrentScreenArea(currentBbox) }
    }

    private suspend fun updateCurrentScreenArea(bbox: BoundingBox?) {
        bbox ?: return
        val tilesRect = bbox.enclosingTilesRect(TILES_ZOOM)
        if (tilesRect.size > 32) return // area too big -> skip (performance)
        if (lastDisplayedRect?.contains(tilesRect) == true) return
        lastDisplayedRect = tilesRect
        // discard all but the last fetch while panning fast (see Android comment)
        updateJob?.cancel()
        updateJob = scope.launch { setQuestPins(tilesRect.asBoundingBox(TILES_ZOOM)) }
    }

    private suspend fun setQuestPins(bbox: BoundingBox) {
        val quests = visibleQuestsSourceMutex.withLock {
            withContext(Dispatchers.IO) { visibleQuestsSource.getAll(bbox) }
        }
        val pins = questsInViewMutex.withLock {
            // keep multi-pin quests with a pin still in view (don't clear) — see Android comment
            questsInView.entries.removeAll { (_, pins) ->
                pins.size == 1 || pins.none { it.position in bbox }
            }
            quests.forEach { questsInView[it.key] = createQuestPins(it) }
            questsInView.values.flatten()
        }
        _pins.value = pins
    }

    private suspend fun updateQuestPins(added: Collection<Quest>, removed: Collection<QuestKey>) {
        val pins = questsInViewMutex.withLock {
            val displayedBBox = lastDisplayedRect?.asBoundingBox(TILES_ZOOM) ?: return
            var hasChanges = false
            removed.forEach { if (questsInView.remove(it) != null) hasChanges = true }
            added.forEach {
                if (displayedBBox.contains(it.position)) {
                    questsInView[it.key] = createQuestPins(it); hasChanges = true
                } else if (questsInView.remove(it.key) != null) hasChanges = true
            }
            if (!hasChanges) return
            questsInView.values.flatten()
        }
        _pins.value = pins
    }

    private fun initializeQuestTypeOrders() {
        val sortedQuestTypes = questTypeRegistry.toMutableList()
        questTypeOrderSource.sort(sortedQuestTypes)
        questTypeOrdersLock.withLock {
            questTypeOrders.clear()
            sortedQuestTypes.forEachIndexed { index, questType -> questTypeOrders[questType] = index }
        }
    }

    private fun createQuestPins(quest: Quest): List<Pin> {
        val props = quest.key.toProperties()
        val order = questTypeOrdersLock.withLock { questTypeOrders[quest.type] ?: 0 }
        val iconName = quest.type.icon.pinImageName()
        return quest.markerLocations.map { Pin(it, iconName, props, order) }
    }

    private fun reinitializeQuestTypeOrders() {
        initializeQuestTypeOrders()
        invalidate()
    }

    companion object {
        private const val TILES_ZOOM = 16
    }
}

private const val MARKER_QUEST_GROUP = "quest_group"
private const val MARKER_ELEMENT_TYPE = "element_type"
private const val MARKER_ELEMENT_ID = "element_id"
private const val MARKER_QUEST_TYPE = "quest_type"
private const val MARKER_NOTE_ID = "note_id"
private const val QUEST_GROUP_OSM = "osm"
private const val QUEST_GROUP_OSM_NOTE = "osm_note"

private fun QuestKey.toProperties(): List<Pair<String, String>> = when (this) {
    is OsmNoteQuestKey -> listOf(
        MARKER_QUEST_GROUP to QUEST_GROUP_OSM_NOTE,
        MARKER_NOTE_ID to noteId.toString(),
    )
    is OsmQuestKey -> listOf(
        MARKER_QUEST_GROUP to QUEST_GROUP_OSM,
        MARKER_ELEMENT_TYPE to elementType.name,
        MARKER_ELEMENT_ID to elementId.toString(),
        MARKER_QUEST_TYPE to questTypeName,
    )
}

private fun Map<String, String>.toQuestKey(): QuestKey? = when (get(MARKER_QUEST_GROUP)) {
    QUEST_GROUP_OSM_NOTE -> OsmNoteQuestKey(getValue(MARKER_NOTE_ID).toLong())
    QUEST_GROUP_OSM -> OsmQuestKey(
        ElementType.valueOf(getValue(MARKER_ELEMENT_TYPE)),
        getValue(MARKER_ELEMENT_ID).toLong(),
        getValue(MARKER_QUEST_TYPE),
    )
    else -> null
}
