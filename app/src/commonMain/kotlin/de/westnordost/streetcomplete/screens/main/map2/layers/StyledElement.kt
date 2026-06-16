package de.westnordost.streetcomplete.screens.main.map2.layers

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.ElementKey
import de.westnordost.streetcomplete.data.overlays.OverlayStyle
import kotlinx.serialization.json.JsonPrimitive

data class StyledElement(
    val element: Element,
    val geometry: ElementGeometry,
    val overlayStyle: OverlayStyle
)

private fun createProperties(key: ElementKey): MutableMap<String, JsonPrimitive> {
    val p = HashMap<String, JsonPrimitive>()
    p[ELEMENT_ID] = JsonPrimitive(key.id)
    p[ELEMENT_TYPE] = JsonPrimitive(key.type.name)
    return p
}

/** mimics width of line as seen in StreetComplete map style */
private fun getLineWidth(tags: Map<String, String>): Float = when (tags["highway"]) {
    "motorway" -> 8f
    "motorway_link" -> 4f
    "trunk", "primary", "secondary", "tertiary" -> 6f
    "service", "track", "busway" -> 3f
    "path", "cycleway", "footway", "bridleway", "steps" -> 1.0f
    null -> 2f
    else -> 4f
}

private fun isBridge(tags: Map<String, String>): Boolean =
    tags["bridge"] != null && tags["bridge"] != "no"

private fun Color.darkened(): Color = Color(
    red = red * 0.67f,
    green = green * 0.67f,
    blue = blue * 0.67f,
    alpha = alpha
)

private fun Color.toRgbaString(): String {
    val c = toArgb()
    return "rgba(${(c shr 16) and 0xFF}, ${(c shr 8) and 0xFF}, ${c and 0xFF}, ${alpha})"
}

private const val ELEMENT_TYPE = "element_type"
private const val ELEMENT_ID = "element_id"
