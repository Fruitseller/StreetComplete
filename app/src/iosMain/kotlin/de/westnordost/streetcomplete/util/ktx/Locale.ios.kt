package de.westnordost.streetcomplete.util.ktx

import androidx.compose.ui.text.intl.Locale
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleIdentifier
import platform.Foundation.NSLocaleCountryCode
import platform.Foundation.NSLocaleLanguageCode
import platform.Foundation.NSLocaleScriptCode

/** The [NSLocale] backing this Compose [Locale].
 *
 *  Compose Multiplatform made `Locale.platformLocale` `internal` in 1.11, so reconstruct the
 *  backing [NSLocale] from the BCP-47 language tag instead (NSLocale canonicalizes the hyphen vs.
 *  underscore identifier forms, so this is equivalent to the old `platformLocale`). */
internal fun Locale.toNSLocale(): NSLocale = NSLocale(localeIdentifier = toLanguageTag())

actual fun Locale.getDisplayName(locale: Locale): String? =
    locale.toNSLocale().displayNameForKey(NSLocaleIdentifier, locale.toLanguageTag())

actual fun Locale.getDisplayLanguage(locale: Locale): String? =
    locale.toNSLocale().displayNameForKey(NSLocaleLanguageCode, locale.language)

actual fun Locale.getDisplayRegion(locale: Locale): String? =
    locale.toNSLocale().displayNameForKey(NSLocaleCountryCode, locale.region)

actual fun Locale.getDisplayScript(locale: Locale): String? =
    locale.toNSLocale().displayNameForKey(NSLocaleScriptCode, locale.script)
