package de.westnordost.streetcomplete

import androidx.compose.material.Text
import androidx.compose.ui.window.ComposeUIViewController
import de.westnordost.streetcomplete.ui.theme.AppTheme
import platform.UIKit.UIViewController

/** Entry point of the iOS app: hosts the Compose UI in a UIViewController.
 *  Surfaces to Swift as `MainViewControllerKt.MainViewController()`. */
fun MainViewController(): UIViewController = ComposeUIViewController {
    AppTheme {
        Text("Hello from Compose")
    }
}
