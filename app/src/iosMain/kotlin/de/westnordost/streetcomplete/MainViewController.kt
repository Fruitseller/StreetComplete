package de.westnordost.streetcomplete

import androidx.compose.ui.window.ComposeUIViewController
import de.westnordost.streetcomplete.di.initKoin
import de.westnordost.streetcomplete.screens.about.AboutScreen
import de.westnordost.streetcomplete.ui.theme.AppTheme
import platform.UIKit.UIViewController

/** Entry point of the iOS app: hosts the Compose UI in a UIViewController.
 *  Surfaces to Swift as `MainViewControllerKt.MainViewController()`. */
fun MainViewController(): UIViewController {
    initKoin()
    return ComposeUIViewController {
        AppTheme {
            // First real shared screen on iOS. Navigation targets are no-ops for now;
            // they lead to screens whose ViewModels need data modules not yet wired for iOS.
            AboutScreen(
                onClickChangelog = {},
                onClickCredits = {},
                onClickPrivacyStatement = {},
                onClickLogs = {},
                onClickBack = {},
            )
        }
    }
}
