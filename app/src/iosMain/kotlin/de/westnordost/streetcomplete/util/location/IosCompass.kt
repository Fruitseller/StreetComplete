package de.westnordost.streetcomplete.util.location

import de.westnordost.streetcomplete.data.location.Compass
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreLocation.CLHeading
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.darwin.NSObject

/** iOS [Compass] backed by CLLocationManager heading updates. trueHeading already has magnetic
 *  declination applied by iOS (unlike Android, which applies GeomagneticField manually). */
class IosCompass : Compass {
    private val _rotation = MutableStateFlow<Float?>(null)
    override val rotation: StateFlow<Float?> = _rotation.asStateFlow()

    private val manager = CLLocationManager()
    private val delegate = Delegate()

    init {
        manager.delegate = delegate
        manager.headingFilter = 1.0 // emit on ≥1° change
    }

    override fun start() { manager.startUpdatingHeading() }
    override fun stop() { manager.stopUpdatingHeading() }

    private inner class Delegate : NSObject(), CLLocationManagerDelegateProtocol {
        override fun locationManager(manager: CLLocationManager, didUpdateHeading: CLHeading) {
            val heading = if (didUpdateHeading.trueHeading >= 0.0) didUpdateHeading.trueHeading
                          else didUpdateHeading.magneticHeading
            _rotation.value = heading.toFloat()
        }
    }
}
