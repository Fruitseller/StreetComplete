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
        // only emit on ≥1 m movement (parity with androidMain FineLocationManager's minDistance=1f);
        // the default kCLDistanceFilterNone would fire on every GPS-noise sample and repeatedly cancel
        // the follow animation in MapScreen.
        manager.distanceFilter = 1.0
        manager.delegate = delegate
        _hasPermission.value = manager.authorizationStatus.isAuthorized()
    }

    override fun requestPermission() { manager.requestWhenInUseAuthorization() }
    override fun start() { manager.startUpdatingLocation() }
    // clear the last fix so a re-created consumer (singleton outlives the ViewModel) starts in SEARCHING
    // and the follow camera doesn't jump to a stale position before a fresh fix arrives.
    override fun stop() { manager.stopUpdatingLocation(); _location.value = null }

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
        // monotonic (like Android's elapsedRealtimeNanos), NOT wall-clock, so RecentLocations dedup works.
        // TODO M3b.x: subtract this fix's age (CLLocation.timestamp) so a cached fix iOS delivers on start()
        // isn't stamped "now" — latent until RecentLocations/SurveyChecker are wired on iOS; needs a
        // K/N-resolvable NSDate interval API (timeIntervalSince*/timeIntervalSinceNow don't resolve here).
        elapsedDuration = NSProcessInfo.processInfo.systemUptime.seconds,
        bearing = if (course >= 0.0) course.toFloat() else null,
    )
}
