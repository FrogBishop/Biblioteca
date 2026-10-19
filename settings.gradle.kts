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

// One compiled artifact can serve every release in a group when the
// intermediary names and the Java/API era do not change. These are the
// representative nodes that are built by default; each one also emits a
// grouped jar whose fabric.mod.json accepts the whole range.
data class JarGroup(
    val id: String,
    val representative: String,
    val members: List<String>,
    val minecraft: String,
    val noRender: Boolean = false,
)

val jarGroups = listOf(
    JarGroup("1.20.4", "1.20.4", listOf("1.20.4"), "1.20.4"),
    JarGroup(
        "1.20.5-1.21.1", "1.20.5",
        listOf("1.20.5", "1.20.6", "1.21", "1.21.1"),
        ">=1.20.5 <1.21.2",
    ),
    JarGroup(
        "1.21.2-1.21.4", "1.21.2",
        listOf("1.21.2", "1.21.3", "1.21.4"),
        ">=1.21.2 <1.21.5",
    ),
    JarGroup(
        "1.21.5-1.21.10", "1.21.5",
        // 1.21.9 still needs its intermediary artifact; it is listed here
        // because the 1.21.5 binary is the one that will serve it.
        listOf("1.21.5", "1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10"),
        ">=1.21.5 <1.21.11",
    ),
    JarGroup("1.21.11", "1.21.11", listOf("1.21.11"), "1.21.11"),
    // 26.3 keeps the full renderer in its own jar; the common jar is the
    // server / 26.1-26.2 client artifact.
    JarGroup(
        "26.1-26.3", "26.1",
        listOf("26.1", "26.1.1", "26.1.2", "26.2", "26.3"),
        ">=26.1 <26.4",
        noRender = true,
    ),
)

val fullRendererVersion = "26.3"

// Exposed to build.gradle.kts, which is evaluated once per version node.
gradle.rootProject {
    extensions.extraProperties.set(
        "bibliotecaJarGroups",
        jarGroups.map {
            mapOf(
                "id" to it.id,
                "representative" to it.representative,
                "members" to it.members,
                "minecraft" to it.minecraft,
                "noRender" to it.noRender,
            )
        },
    )
}
val defaultVersions = (jarGroups.map { it.representative } + fullRendererVersion).distinct()

// Only versions whose source port is complete are part of the default build.
// Set SC_VERSIONS=... or SC_ALL=1 for individual nodes / the whole matrix.
val supportedVersions = listOf(
    "1.20.4", "1.20.5", "1.20.6",
    "1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4",
    "1.21.5", "1.21.6", "1.21.7", "1.21.8",
    // 1.21.9 is waiting on its intermediary artifact; add it once fetched.
    "1.21.10", "1.21.11",
    "26.1", "26.1.1", "26.1.2", "26.2", "26.3",
)

val selected = when {
    System.getenv("SC_ALL") == "1" -> allVersions
    System.getenv("SC_VERSIONS") != null -> System.getenv("SC_VERSIONS")!!
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    else -> defaultVersions
}.toMutableList().apply {
    // Stonecutter's active version must always be present; 26.3 is the
    // workspace version the sources are edited in.
    if ("26.3" !in this) add("26.3")
}

stonecutter {
    create(rootProject) {
        versions(*selected.toTypedArray())
        vcsVersion = if ("26.3" in selected) "26.3" else selected.last()
    }
}

rootProject.name = "biblioteca"
