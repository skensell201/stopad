pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "StopAd"
include(":app")
// Isolated emulator fixtures; never included in ordinary application builds.
if (providers.gradleProperty("withProbe").isPresent) {
    include(":probe")
    project(":probe").projectDir = file("tools/probe")
}
