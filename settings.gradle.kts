pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "EntglDb.Kotlin"

include(":core")
include(":persistence-sqlite")
include(":network")
include(":protocol")
include(":app")
