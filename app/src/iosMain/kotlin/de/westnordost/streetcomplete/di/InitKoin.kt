package de.westnordost.streetcomplete.di

import de.westnordost.streetcomplete.iosModule
import de.westnordost.streetcomplete.data.logs.logsModule
import de.westnordost.streetcomplete.screens.about.aboutScreenModule
import de.westnordost.streetcomplete.util.logs.IosLogger
import de.westnordost.streetcomplete.util.logs.Log
import org.koin.core.context.startKoin

private var koinStarted = false

/** Starts Koin for iOS exactly once. Safe to call from every MainViewController()
 *  invocation (Compose may recreate the hosting UIViewController). */
fun initKoin() {
    if (koinStarted) return
    koinStarted = true

    Log.instances.add(IosLogger())

    startKoin {
        modules(iosModule, aboutScreenModule, logsModule)
    }
}
