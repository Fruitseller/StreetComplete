package de.westnordost.streetcomplete.data.location

import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import kotlin.time.Duration

data class Location(
    val position: LatLon,
    val accuracy: Float,
    val elapsedDuration: Duration,
    /** GPS course (direction of travel), degrees clockwise from north; null when unknown/stationary */
    val bearing: Float? = null,
)
