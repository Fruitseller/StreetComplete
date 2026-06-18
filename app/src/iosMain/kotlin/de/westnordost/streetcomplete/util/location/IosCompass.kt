package de.westnordost.streetcomplete.util.location

import de.westnordost.streetcomplete.data.location.Compass
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreLocation.CLHeading
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLLocationAccuracyKilometer
import platform.darwin.NSObject

/** iOS [Compass] backed by CLLocationManager heading updates. We report `trueHeading` (magnetic
 *  declination already applied by iOS, unlike Android's manual GeomagneticField) — but `trueHeading`
 *  is only valid once the manager is ALSO receiving location updates (iOS needs the position to know
 *  the local declination). So this manager runs a deliberately cheap location stream (kilometre
 *  accuracy, 1 km filter — enough for declination, negligible battery) alongside heading; without it
 *  `trueHeading` stays −1 and we'd fall back to uncorrected `magneticHeading`. */
class IosCompass : Compass {
    private val _rotation = MutableStateFlow<Float?>(null)
    override val rotation: StateFlow<Float?> = _rotation.asStateFlow()

    private val manager = CLLocationManager()
    private val delegate = Delegate()

    init {
        manager.delegate = delegate
        manager.headingFilter = 1.0 // emit on ≥1° change
        manager.desiredAccuracy = kCLLocationAccuracyKilometer // coarse: only to enable trueHeading
        manager.distanceFilter = 1000.0
    }

    override fun start() { manager.startUpdatingLocation(); manager.startUpdatingHeading() }
    override fun stop() { manager.stopUpdatingHeading(); manager.stopUpdatingLocation(); _rotation.value = null }

    private inner class Delegate : NSObject(), CLLocationManagerDelegateProtocol {
        override fun locationManager(manager: CLLocationManager, didUpdateHeading: CLHeading) {
            val heading = if (didUpdateHeading.trueHeading >= 0.0) didUpdateHeading.trueHeading
                          else didUpdateHeading.magneticHeading
            _rotation.value = heading.toFloat()
        }
    }
}
