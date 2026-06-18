package de.westnordost.streetcomplete.data.location

import kotlinx.coroutines.flow.StateFlow

/** Thin provider of device location updates. The platform implementation is a dumb wrapper
 *  (Android: FineLocationManager; iOS: CLLocationManager); the LocationState / follow logic
 *  lives in the consumer (MapViewModel), mirroring Android's MainActivity/MainViewModel. */
interface LocationSource {
    /** the most recent fix; null until the first one arrives after [start] */
    val location: StateFlow<Location?>
    /** whether the app currently holds location permission (authorizedWhenInUse/Always) */
    val hasPermission: StateFlow<Boolean>
    /** ask the OS for location permission; no-op if already granted */
    fun requestPermission()
    /** begin receiving location updates (requires permission) */
    fun start()
    /** stop receiving location updates */
    fun stop()
}
