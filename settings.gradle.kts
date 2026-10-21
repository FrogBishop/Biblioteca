pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
    // Bridges the remapping Loom used before 26.1 and the unobfuscated Loom used after.
    id("dev.kikugie.loom-back-compat") version "0.4.2"
}

val allVersions = listOf(
    "1.20.4", "1.20.5", "1.20.6",
    "1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4",
    "1.21.5", "1.21.6", "1.21.7", "1.21.8",
    "1.21.9", "1.21.10", "1.21.11",
    "26.1", "26.1.1", "26.1.2", "26.2", "26.3",
)

// Render and networking ABIs change within the former broad groups. Each release
// gets an exact-version full artifact and a matching no-render artifact.
val selected = when {
    System.getenv("SC_VERSIONS") != null -> System.getenv("SC_VERSIONS")!!
        .split(',').map { it.trim() }.filter { it.isNotEmpty() }
    else -> allVersions
}.toMutableList().apply {
    require(all { it in allVersions }) { "Unknown Minecraft node in SC_VERSIONS" }
    if ("26.3" !in this) add("26.3")
}

stonecutter {
    create(rootProject) {
        versions(*selected.distinct().toTypedArray())
        vcsVersion = "26.3"
    }
}

rootProject.name = "biblioteca"
