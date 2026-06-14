package de.westnordost.streetcomplete.screens.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.action_about2
import de.westnordost.streetcomplete.resources.action_settings
import de.westnordost.streetcomplete.screens.about.AboutNavHost
import de.westnordost.streetcomplete.screens.settings.SettingsNavHost
import org.jetbrains.compose.resources.stringResource

/** Top-level shared navigation shell for platforms without the Fragment-based main screen
 *  (currently iOS). Switches between the section NavHosts; each section manages its own
 *  internal back stack, mirroring the one-Activity-per-section model on Android. */
@Composable
fun MainMenuNavHost() {
    val navController = rememberNavController()
    fun goBack() { navController.popBackStack() }

    NavHost(navController = navController, startDestination = MainMenuDestination.Menu) {
        composable(MainMenuDestination.Menu) {
            MainMenuScreen(
                onClickSettings = { navController.navigate(MainMenuDestination.Settings) },
                onClickAbout = { navController.navigate(MainMenuDestination.About) },
            )
        }
        composable(MainMenuDestination.About) {
            AboutNavHost(onClickBack = ::goBack)
        }
        composable(MainMenuDestination.Settings) {
            SettingsNavHost(onClickBack = ::goBack, onClickShowQuestTypeForDebug = {})
        }
    }
}

@Composable
private fun MainMenuScreen(
    onClickSettings: () -> Unit,
    onClickAbout: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("StreetComplete") }) }
    ) { padding ->
        Column(Modifier.fillMaxWidth().padding(padding)) {
            MenuRow(stringResource(Res.string.action_settings), onClickSettings)
            MenuRow(stringResource(Res.string.action_about2), onClickAbout)
        }
    }
}

@Composable
private fun MenuRow(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.h6,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(24.dp)
    )
}

object MainMenuDestination {
    const val Menu = "menu"
    const val About = "about"
    const val Settings = "settings"
    const val User = "user"
}
