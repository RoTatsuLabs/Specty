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
        // FFmpegKitNext, built by RoTatsuLabs/Forge. Reading it needs a token with read:packages.
        maven {
            name = "forge"
            url = uri("https://maven.pkg.github.com/RoTatsuLabs/Forge")
            credentials {
                username = providers.gradleProperty("forge.user").orElse(providers.environmentVariable("FORGE_PACKAGES_USER")).orNull
                password = providers.gradleProperty("forge.token").orElse(providers.environmentVariable("FORGE_PACKAGES_TOKEN")).orNull
            }
            content { includeGroup("com.rotatsulabs.forge") }
        }
    }
}

rootProject.name = "Specty"
include(":app")
include(":core:audio")
include(":core:data")
include(":core:design")
