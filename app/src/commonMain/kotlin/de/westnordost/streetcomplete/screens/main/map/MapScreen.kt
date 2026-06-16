package de.westnordost.streetcomplete.screens.main.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.westnordost.streetcomplete.screens.main.map2.Map
import de.westnordost.streetcomplete.ui.common.BackIcon

/** iOS map screen (M3b.2a): StreetComplete's own programmatic vector style. */
@Composable
fun MapScreen(onClickBack: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Map(modifier = Modifier.fillMaxSize())
        IconButton(
            onClick = onClickBack,
            modifier = Modifier.safeDrawingPadding().padding(8.dp),
        ) { BackIcon() }
    }
}
