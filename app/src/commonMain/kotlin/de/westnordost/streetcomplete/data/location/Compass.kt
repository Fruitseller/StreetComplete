package de.westnordost.streetcomplete.data.location

import kotlinx.coroutines.flow.StateFlow

/** Thin provider of device heading. Mirrors androidMain screens/main/map/Compass.
 *  [rotation] = degrees clockwise from true north, magnetic declination already applied;
 *  null when no heading is available. */
interface Compass {
    val rotation: StateFlow<Float?>
    fun start()
    fun stop()
}
