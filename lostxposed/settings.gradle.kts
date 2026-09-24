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
    }
}

rootProject.name = "lostxposed"

// Loaded into system_server and every hooked app process. Keep it small.
include(":core:api")
include(":core:compat")
include(":core:config")
include(":core:diagnostics")
include(":core:engine")
include(":core:safety")

// Feature implementations. One Gradle module each.
include(":features:noop")
include(":features:displayprofiles")
include(":features:textengine")
include(":features:powerinspector")
include(":features:notificationrules")
include(":features:hardwarekeys")
include(":features:smartstatusbar")

// Framework entry points.
include(":entry:xposed")

// UI process only. Never loaded into a hooked process.
include(":app")


