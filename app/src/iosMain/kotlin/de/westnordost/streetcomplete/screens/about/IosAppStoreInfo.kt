package de.westnordost.streetcomplete.screens.about

class IosAppStoreInfo : AppStoreInfo {
    // The app has no App Store presence yet, so there is no rating URI. Once it is
    // published, return "https://apps.apple.com/app/id<APP_ID>?action=write-review".
    override fun getRatingUri(): String? = null

    override fun disallowsInAppDonationLinks(): Boolean =
        true
}
