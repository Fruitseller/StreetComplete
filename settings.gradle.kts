rootProject.name = "StreetComplete"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        // Patched maplibre-compose fork published locally as 0.13.0-sc1 (adds the public
        // StyleState.addImage/removeImage passthroughs for data-driven named images). Temporary
        // until the change lands upstream; a fresh checkout/CI must publish it first — see
        // app/build.gradle.kts's maplibre-compose dependency and .git/sdd/task-0.1-report.md.
        mavenLocal()
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":app")
