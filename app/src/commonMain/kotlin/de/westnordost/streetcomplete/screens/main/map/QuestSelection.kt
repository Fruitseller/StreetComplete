package de.westnordost.streetcomplete.screens.main.map

import androidx.compose.runtime.Stable
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.quest.QuestKey

/** The currently selected quest on the map: enough to highlight its pin(s) ([icon] + [markerLocations]),
 *  outline + focus its [geometry], and identify it ([questKey]). Display-only — the answer form is a
 *  separate, upstream-blocked concern. Mirrors what Android's MainActivity.showQuestDetails() needs. */
@Stable
data class QuestSelection(
    val questKey: QuestKey,
    val icon: String,
    val markerLocations: List<LatLon>,
    val geometry: ElementGeometry,
)
