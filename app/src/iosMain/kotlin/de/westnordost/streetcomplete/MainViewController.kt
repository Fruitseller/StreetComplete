package de.westnordost.streetcomplete

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import de.westnordost.streetcomplete.di.initKoin
import de.westnordost.streetcomplete.screens.main.MainMenuNavHost
import de.westnordost.streetcomplete.ui.theme.AppTheme
import platform.UIKit.UIViewController

/** Entry point of the iOS app: hosts the Compose UI in a UIViewController.
 *  Surfaces to Swift as `MainViewControllerKt.MainViewController()`. */
fun MainViewController(): UIViewController {
    initKoin()
    return ComposeUIViewController {
        AppTheme {
            // Provide a themed Surface so screens that aren't wrapped in their own Scaffold/Surface
            // (e.g. SettingsScreen is a plain Column) inherit the correct background + content color.
            // On Android the host Activity's window supplies this; on iOS we must add it ourselves,
            // otherwise default-colored text falls back to black and is invisible in dark mode.
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
                MainMenuNavHost()
            }
        }
    }
}
