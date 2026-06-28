package de.westnordost.streetcomplete.screens.main.map2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.allDrawableResources
import de.westnordost.streetcomplete.resources.pin
import de.westnordost.streetcomplete.resources.pin_shadow
import de.westnordost.streetcomplete.screens.main.map2.layers.Pin
import kotlin.math.ceil
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.maplibre.compose.style.StyleState

/** Single source of truth for image NAME <-> DrawableResource, backed by the generated
 *  resource map. The manager goes resource -> name; the registry goes name -> resource;
 *  round-trip is guaranteed because both use this map. */
private val drawableNamesByResource: Map<DrawableResource, String> by lazy {
    Res.allDrawableResources.entries.associate { (name, resource) -> resource to name }
}

/** The image name a pin uses for this quest icon — the compose-resources entry name
 *  (e.g. "quest_recycling"), matching Android's getResourceEntryName. */
fun DrawableResource.pinImageName(): String =
    drawableNamesByResource[this] ?: error("Drawable not in Res.allDrawableResources: $this")

/** Registers, by name, the composited pin bitmap for every quest icon currently in [pins].
 *  Lazy (only icons actually present), deduped per (styleEpoch, name), re-run on style reload. */
@Composable
fun PinImageRegistry(styleState: StyleState, styleEpoch: Int, pins: Collection<Pin>) {
    val iconsByName: Map<String, DrawableResource> = remember(pins) {
        pins.mapNotNull { p -> Res.allDrawableResources[p.icon]?.let { p.icon to it } }.toMap()
    }
    iconsByName.forEach { (name, resource) ->
        key(name) {
            val bitmap = rememberPinBitmap(resource)
            LaunchedEffect(styleEpoch, name, bitmap) {
                styleState.addImage(name, bitmap, sdf = false)
            }
        }
    }
}

/** Compose port of androidMain MapIconBitmapCreator.createPinBitmap: shadow + pin body + quest icon
 *  composited into one 71dp-square bitmap, non-SDF. Geometry copied verbatim from the Android rects. */
@Composable
fun rememberPinBitmap(icon: DrawableResource): ImageBitmap {
    val density = LocalDensity.current
    val shadow = painterResource(Res.drawable.pin_shadow)
    val pin = painterResource(Res.drawable.pin)
    val iconPainter = painterResource(icon)
    return remember(icon, density) { drawPinBitmap(density, shadow, pin, iconPainter) }
}

private fun drawPinBitmap(density: Density, shadow: Painter, pin: Painter, icon: Painter): ImageBitmap {
    val size = with(density) { 71.dp.toPx() }
    val sizeInt = ceil(size).toInt()
    val iconSize = with(density) { 48.dp.toPx() }
    val iconPinOffset = with(density) { 2.dp.toPx() }
    val pinTopRightPadding = with(density) { 5.dp.toPx() }
    // pin.xml intrinsic ratio is 52:66 (density cancels in the ratio)
    val pinWidth = (size - pinTopRightPadding) * (pin.intrinsicSize.width / pin.intrinsicSize.height)
    val pinXOffset = size - pinTopRightPadding - pinWidth

    val bitmap = ImageBitmap(sizeInt, sizeInt)
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap), Size(size, size)) {
        with(shadow) { draw(Size(size, size)) }
        translate(pinXOffset, pinTopRightPadding) {
            with(pin) { draw(Size(pinWidth, size - pinTopRightPadding)) }
        }
        translate(pinXOffset + iconPinOffset, pinTopRightPadding + iconPinOffset) {
            with(icon) { draw(Size(iconSize, iconSize)) }
        }
    }
    return bitmap
}
