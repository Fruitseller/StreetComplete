package de.westnordost.streetcomplete.data.meta

import de.westnordost.countryboundaries.CountryBoundaries
import de.westnordost.osmfeatures.FeatureDictionary
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.source
import org.koin.dsl.module
import platform.Foundation.NSBundle

actual val metadataPlatformModule = module {
    // compose-resources are bundled under composeResources/<packageOfResClass>/ (see
    // app/build.gradle.kts compose.resources.packageOfResClass). boundaries.ser + osmfeatures are
    // read here as raw files via kotlinx.io (not through `Res`), so the full nested path is needed.
    val dir = NSBundle.mainBundle.resourcePath +
        "/compose-resources/composeResources/de.westnordost.streetcomplete.resources/files/"

    single<CountryBoundaries> {
        val file = Path(dir + "boundaries.ser")
        val source = SystemFileSystem.source(file).buffered()
        CountryBoundaries.deserializeFrom(source)
    }

    single<FeatureDictionary> {
        FeatureDictionary.create(
            fileSystem = SystemFileSystem,
            presetsBasePath = dir + "osmfeatures/default",
            brandPresetsBasePath = dir + "osmfeatures/brands"
        )
    }
}
