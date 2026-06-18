@file:OptIn(ExperimentalForeignApi::class)

package de.westnordost.streetcomplete.util.location

import de.westnordost.streetcomplete.data.location.Location
import de.westnordost.streetcomplete.data.location.LocationSource
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration.Companion.seconds
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.Foundation.NSProcessInfo
import platform.darwin.NSObject

/** iOS [LocationSource] backed by CLLocationManager. Dumb wrapper: delivers fixes + permission
 *  state only; LocationState/follow logic lives in MapViewModel (parity with androidMain's
 *  FineLocationManager being a dumb wrapper used by MainActivity/MainViewModel). */
class IosLocationSource : LocationSource {
    private val _location = MutableStateFlow<Location?>(null)
    override val location: StateFlow<Location?> = _location.asStateFlow()

    private val _hasPermission = MutableStateFlow(false)
    override val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private val manager = CLLocationManager()
    private val delegate = Delegate()

    init {
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.delegate = delegate
        _hasPermission.value = manager.authorizationStatus.isAuthorized()
    }

    override fun requestPermission() { manager.requestWhenInUseAuthorization() }
    override fun start() { manager.startUpdatingLocation() }
    override fun stop() { manager.stopUpdatingLocation() }

    /** retained strongly by the (singleton) IosLocationSource, so the ObjC weak delegate stays alive */
    private inner class Delegate : NSObject(), CLLocationManagerDelegateProtocol {
        override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
            val clLocation = didUpdateLocations.lastOrNull() as? CLLocation ?: return
            _location.value = clLocation.toLocation()
        }
        override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
            _hasPermission.value = manager.authorizationStatus.isAuthorized()
        }
    }
}

private fun CLAuthorizationStatus.isAuthorized(): Boolean =
    this == kCLAuthorizationStatusAuthorizedWhenInUse || this == kCLAuthorizationStatusAuthorizedAlways

private fun CLLocation.toLocation(): Location {
    val (lat, lon) = coordinate.useContents { latitude to longitude }
    return Location(
        position = LatLon(latitude = lat, longitude = lon),
        accuracy = maxOf(0f, horizontalAccuracy.toFloat()),
        // monotonic (like Android's elapsedRealtimeNanos), NOT wall-clock, so RecentLocations dedup works
        elapsedDuration = NSProcessInfo.processInfo.systemUptime.seconds,
        bearing = if (course >= 0.0) course.toFloat() else null,
    )
}
