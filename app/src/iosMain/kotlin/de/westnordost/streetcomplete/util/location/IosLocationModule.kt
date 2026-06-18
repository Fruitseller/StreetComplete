package de.westnordost.streetcomplete.util.location

import de.westnordost.streetcomplete.data.location.Compass
import de.westnordost.streetcomplete.data.location.LocationSource
import org.koin.dsl.module

/** Binds the iOS device-sensor providers. Bound only on iOS — the new map stack (MapViewModel)
 *  is not loaded on Android, so these interfaces are never resolved there. */
val iosLocationModule = module {
    single<LocationSource> { IosLocationSource() }
    single<Compass> { IosCompass() }
}
