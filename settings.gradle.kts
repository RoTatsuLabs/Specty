pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // FFmpegKitNext, built by this repository (ffmpegkit.yml). GitHub Packages needs a token with read:packages, even for public packages.
        maven {
            name = "specty"
            url = uri("https://maven.pkg.github.com/RoTatsuLabs/Specty")
            credentials {
                username = providers.gradleProperty("specty.user").orElse(providers.environmentVariable("SPECTY_PACKAGES_USER")).orNull
                password = providers.gradleProperty("specty.token").orElse(providers.environmentVariable("SPECTY_PACKAGES_TOKEN")).orNull
            }
            content { includeGroup("com.rotatsulabs.specty") }
        }
    }
}

rootProject.name = "Specty"
include(":app")
include(":core:audio")
include(":core:data")
include(":core:design")
