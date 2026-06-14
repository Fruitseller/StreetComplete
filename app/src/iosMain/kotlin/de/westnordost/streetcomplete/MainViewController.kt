package de.westnordost.streetcomplete

import androidx.compose.ui.window.ComposeUIViewController
import de.westnordost.streetcomplete.di.initKoin
import de.westnordost.streetcomplete.screens.about.AboutNavHost
import de.westnordost.streetcomplete.ui.theme.AppTheme
import platform.UIKit.UIViewController

/** Entry point of the iOS app: hosts the Compose UI in a UIViewController.
 *  Surfaces to Swift as `MainViewControllerKt.MainViewController()`. */
fun MainViewController(): UIViewController {
    initKoin()
    return ComposeUIViewController {
        AppTheme {
            // Full About section: Changelog / Credits / Privacy / Logs reachable via the shared NavHost.
            // onClickBack is a no-op here because About is currently the root of the iOS app.
            AboutNavHost(onClickBack = {})
        }
    }
}
