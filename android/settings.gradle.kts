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
rootProject.name = "apper"
include(":felles", ":verktoy", ":sovn", ":gratone", ":notater", ":avganger", ":butikk", ":utklipp", ":vaer", ":sms", ":stille", ":skjermtid", ":vakt")
