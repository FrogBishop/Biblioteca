import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.net.URI
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    // Applies the remapping Loom variant for obfuscated versions and the plain
    // Loom variant for 26.1+, where Minecraft ships unobfuscated.
    id("dev.kikugie.loom-back-compat")
}

version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = property("mod.id") as String

val requiredJava: JavaVersion = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
    else -> JavaVersion.VERSION_17
}

repositories {
    // Fallback for the occasional Fabric Maven artifact that times out on a
    // cold download; populated by tools/fetch-intermediary.ps1.
    maven {
        name = "Local intermediary fallback"
        url = uri(rootProject.layout.projectDirectory.dir("local-repo"))
        content { includeGroup("net.fabricmc") }
    }
    mavenCentral()
}

// Legacy (obfuscated) nodes need a refmap so the mojmap targets written in the
// mixins are remapped to intermediary names inside the jar. The 26.1+ nodes are
// unobfuscated and do not need one.
if (sc.current.parsed < "26.1") {
    loom {
        mixin {
            // Loom 1.18 no longer enables the mixin AP implicitly; the
            // obfuscated nodes need it to write the refmap the runtime uses.
            useLegacyMixinAp.set(true)
            defaultRefmapName.set("biblioteca.refmap.json")
        }
    }
}

// ---------------------------------------------------------------------------
// Fabric API
// ---------------------------------------------------------------------------
// Only the modules this mod compiles against are needed; the full Fabric API is
// resolved from its published bundle and the module jars are extracted once per
// version. This keeps the build independent of a Maven repository that may be
// unavailable and avoids downloading every Fabric API module for every node.
fun fabricApiModules(fabricApiVersion: String, bundleUrl: String): List<File> {
    val cache = rootProject.layout.projectDirectory.dir(".gradle/fabric-api/$fabricApiVersion").asFile
    val wanted = listOf(
        "fabric-api-base-",
        "fabric-command-api-v1-",
        "fabric-command-api-v2-",
        "fabric-lifecycle-events-v1-",
        "fabric-networking-api-v1-",
    )

    cache.mkdirs()
    val bundle = File(cache, "fabric-api.jar")
    if (!bundle.isFile) {
        logger.lifecycle("[biblioteca] downloading Fabric API $fabricApiVersion")
        val connection = URI(bundleUrl).toURL().openConnection()
        connection.connectTimeout = 30_000
        connection.readTimeout = 120_000
        connection.getInputStream().use { input ->
            bundle.outputStream().use { output -> input.copyTo(output) }
        }
    }

    var jars = cache.listFiles { file -> file.isFile && file.extension == "jar" && file.name != "fabric-api.jar" }
        ?.sortedBy { it.name }
        .orEmpty()

    if (jars.isEmpty()) {
        ZipFile(bundle).use { zip ->
            for (entry in zip.entries()) {
                val name = entry.name.substringAfterLast('/')
                if (!entry.name.startsWith("META-INF/jars/") || !entry.name.endsWith(".jar")) continue
                if (wanted.none { name.startsWith(it) }) continue
                File(cache, name).outputStream().use { output ->
                    zip.getInputStream(entry).use { input -> input.copyTo(output) }
                }
            }
        }
        jars = cache.listFiles { file -> file.isFile && file.extension == "jar" && file.name != "fabric-api.jar" }
            ?.sortedBy { it.name }
            .orEmpty()
    }
    check(jars.isNotEmpty()) { "No Fabric API modules were extracted for $fabricApiVersion" }
    return jars
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    // Official mappings on obfuscated versions; a no-op from 26.1 on.
    loomx.applyMojangMappings()

    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")

    val fabricApiVersion = sc.properties["deps.fabric_api"] as String
    val fabricApiUrl = sc.properties["deps.fabric_api_url"] as String
    for (jar in fabricApiModules(fabricApiVersion, fabricApiUrl)) {
        modImplementation(files(jar))
    }
}

java {
    sourceCompatibility = requiredJava
    targetCompatibility = requiredJava
}

// Hooks whose 26.3 target does not exist in an older release. The matching
// legacy or mid-era variant is listed in the same config; this set only removes
// the inapplicable one. The generated mixin config is filtered to match, because
// Mixin would otherwise fail to load the class.
val excludedServerMixins = buildSet {
    if (sc.current.parsed < "26.1") {
        addAll(listOf(
            // Before 26.1 the reach check is inline in ServerPlayerGameMode and
            // the dedicated build-limit message helper does not exist yet;
            // LegacyReachMixin covers the inline check.
            "PlayerReachMixin",
            "ServerPlayerGameModeMixin",
            "ServerPlayerBuildLimitMixin",
        ))
    } else {
        add("LegacyBuildLimitMixin")
        add("LegacyReachMixin")
    }
    if (sc.current.parsed < "1.21.5") {
        addAll(listOf(
            "BeaconBlockEntityAccessor",
            "BeaconBlockEntityYLoopMixin",
        ))
    } else {
        addAll(listOf(
            "LegacyBeaconBeamSectionAccessor",
            "LegacyBeaconBlockEntityAccessor",
            "LegacyBeaconBlockEntityYLoopMixin",
        ))
    }
    if (sc.current.parsed < "1.21.5") {
        add("MidServerLevelEffectsYLoopMixin")
    } else {
        add("LegacyServerLevelEffectsYLoopMixin")
    }
    if (sc.current.parsed >= "26.3") {
        add("MidServerLevelEffectsYLoopMixin")
    }
    if (sc.current.parsed < "26.3") {
        addAll(listOf(
            "ServerLevelEffectsYLoopMixin",
            // 26.3 added explicit isOutsideBuildHeight guards to these growth
            // paths. Older releases do not have those guards; their growth
            // code goes through Level.getBlockState/setBlock, which
            // LevelBlockAccessMixin already folds, so the 26.3 hooks have no
            // call site to attach to there.
            "GrassBlockYLoopMixin",
            "BambooStalkBlockYLoopMixin",
            "GrowingPlantHeadBlockYLoopMixin",
        ))
    }
}

if (excludedServerMixins.isNotEmpty()) {
    sourceSets.named("main") {
        java.exclude(excludedServerMixins.map { "babel/biblioteca/mixin/$it.java" })
    }
}

// The 26.3 client is a render-state/submit renderer. Before that the renderer
// is a different architecture, so a legacy jar keeps the payload cache, the
// create-world screen and the entrypoint. 1.21.1 and 1.21.8 add their own
// renderer ports below; the remaining legacy nodes have gameplay only.
val legacyClientMixins = listOf(
    "ClientLevelDirtyMixin",
    "CreateWorldScreenMoreTabMixin",
    "GridLayoutTabAccessor",
)
// Classic-renderer port for 1.21.1. It is intentionally built only for that
// node until the other legacy releases have their own renderer variants.
val legacy1211RendererMixins = listOf(
    "LegacyLevelRendererYLoopMixin",
    "LegacySectionOcclusionGraphLoopMixin",
    "LegacyViewAreaAccessor",
    "LegacyEntityRender1211YLoopMixin",
    "LegacyBeaconBeam1211YLoopMixin",
    "LegacyFrustumInvoker",
)

// 1.21.8 has the new frame-graph chunk renderer. Its terrain copies are built by
// calling prepareChunkRenders once per period; the chunk render mixin replays them.
val legacy1218RendererMixins = listOf(
    "MidLevelRendererYLoopMixin",
    "MidChunkSectionsToRenderMixin",
    "MidViewAreaAccessor",
    "MidSectionOcclusionGraphLoopMixin",
    "LegacyEntityRender1218YLoopMixin",
    "LegacyBeaconBeam1218YLoopMixin",
    "MidFrustumInvoker",
)
val legacy1218ClientClasses = listOf("MidChunkCopyStore")

// The block-entity dispatcher kept the same render signature across 1.21.1 and
// 1.21.8; include the shared hook in both ported nodes without duplicating its
// source exclusion. Entity renderers differ by one float and are version-specific.
val legacyClassicSharedMixins = listOf(
    "LegacyBlockEntityRenderYLoopMixin",
)

val legacyRendererMixins = listOf(
    "BeaconBeamRenderYLoopMixin",
    "BlockEntityRenderRangeYLoopMixin",
    "ChunkDrawGroupAccessor",
    "CloudRendererLoopMixin",
    "EntityRenderYLoopMixin",
    "EntityShouldRenderYLoopMixin",
    "FrustumInvoker",
    "FrustumYLoopMixin",
    "ImprovedTransparencyYLoopMixin",
    "LevelRendererYLoopMixin",
    "OptionsSubScreenAccessor",
    "RenderRegionCacheYLoopMixin",
    "SectionOcclusionGraphLoopMixin",
    "SkyRendererYLoopMixin",
    "VideoSettingsScreenMixin",
    "ViewAreaAccessor",
    "VoidFogMixin",
)
if (sc.current.parsed < "26.3") {
    sourceSets.named("main") {
        java.exclude(
            "babel/biblioteca/client/LoopingClientSettings.java",
            "babel/biblioteca/client/SeamCull.java",
        )
        java.exclude(legacyRendererMixins.map { "babel/biblioteca/client/mixin/$it.java" })
    }
}
if (sc.current.version != "1.21.1") {
    sourceSets.named("main") {
        java.exclude(legacy1211RendererMixins.map { "babel/biblioteca/client/mixin/$it.java" })
    }
}
if (sc.current.version != "1.21.8") {
    sourceSets.named("main") {
        java.exclude(legacy1218RendererMixins.map { "babel/biblioteca/client/mixin/$it.java" })
        java.exclude(legacy1218ClientClasses.map { "babel/biblioteca/client/$it.java" })
    }
}
if (sc.current.version != "1.21.1" && sc.current.version != "1.21.8") {
    sourceSets.named("main") {
        java.exclude(legacyClassicSharedMixins.map { "babel/biblioteca/client/mixin/$it.java" })
    }
}

// Diagnostic switch: compile the shared/server half of the mod without the
// client entrypoint as well, which is what the server-only smoke builds use.
if (System.getenv("SERVER_ONLY") == "1") {
    sourceSets.named("main") {
        java.exclude("babel/biblioteca/client/**")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(requiredJava.majorVersion.toInt())
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version.toString(),
        "minecraft" to (sc.properties["mod.mc_compat"] as String),
        "loader" to (project.property("deps.fabric_loader") as String),
        "java" to requiredJava.majorVersion,
    )
    inputs.properties(props)
    inputs.property("excludedServerMixins", excludedServerMixins.joinToString(","))
    filesMatching("fabric.mod.json") { expand(props) }
    filesMatching("*.mixins.json") { expand("java" to "JAVA_${requiredJava.majorVersion}") }

    doLast {
        val outputDir = destinationDirectory.get().asFile

        if (excludedServerMixins.isNotEmpty()) {
            val configFile = outputDir.resolve("biblioteca.mixins.json")
            val slurper = groovy.json.JsonSlurper()
            @Suppress("UNCHECKED_CAST")
            val config = slurper.parse(configFile) as MutableMap<String, Any?>
            config["mixins"] = (config["mixins"] as List<*>).filterNot { it in excludedServerMixins }
            // A legacy preview build must not take the game down when an
            // optional hook cannot find its call site; the core hooks still log
            // and apply normally.
            config["injectors"] = mapOf("defaultRequire" to 0)
            configFile.writeText(
                groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(config)) + "\n")
        }

        if (sc.current.parsed < "26.3") {
            // Only client hooks that are compiled for this node may be named in
            // the generated config; asking for a class that was excluded is a
            // startup crash.
            val rendererEntries = when (sc.current.version) {
                "1.21.1" -> legacy1211RendererMixins + legacyClassicSharedMixins
                "1.21.8" -> legacy1218RendererMixins + legacyClassicSharedMixins
                else -> emptyList()
            }
            val clientEntries = (legacyClientMixins + rendererEntries)
                .joinToString(",\n    ") { "\"$it\"" }
            outputDir.resolve("biblioteca.client.mixins.json").writeText(
                """{
  "required": true,
  "package": "babel.biblioteca.client.mixin",
  "compatibilityLevel": "JAVA_${requiredJava.majorVersion}",
  "client": [
    $clientEntries
  ],
  "injectors": {
    "defaultRequire": 0
  }
}
""")
        }
    }
}

tasks.jar {
    val projectName = project.name
    inputs.property("projectName", projectName)
    from(rootProject.file("LICENSE")) {
        rename { "${it}-$projectName" }
    }
}

// ---------------------------------------------------------------------------
// No-render variant jar
// ---------------------------------------------------------------------------
// The dedicated-server jar carries the same logic with the client renderer
// mixins removed. It is cut from the finished jar so the two cannot drift.
val noRenderMixins = listOf(
    "BeaconBeamRenderYLoopMixin",
    "BlockEntityRenderRangeYLoopMixin",
    "ChunkDrawGroupAccessor",
    "CloudRendererLoopMixin",
    "EntityRenderYLoopMixin",
    "EntityShouldRenderYLoopMixin",
    "FrustumInvoker",
    "FrustumYLoopMixin",
    "ImprovedTransparencyYLoopMixin",
    "LevelRendererYLoopMixin",
    "RenderRegionCacheYLoopMixin",
    "SectionOcclusionGraphLoopMixin",
    "SkyRendererYLoopMixin",
    "ViewAreaAccessor",
    "VoidFogMixin",
) + legacy1211RendererMixins + legacy1218RendererMixins + legacyClassicSharedMixins

val noRenderDir = layout.buildDirectory.dir("generated/noRender")

val prepareNoRenderResources = tasks.register("prepareNoRenderResources") {
    group = "build"
    description = "Rewrites the client mixin config and mod metadata for the no-render jar."
    dependsOn(tasks.processResources)
    val resourcesDir = layout.buildDirectory.dir("resources/main")
    inputs.dir(resourcesDir)
    outputs.dir(noRenderDir)
    doLast {
        val out = noRenderDir.get().asFile
        out.mkdirs()
        val slurper = groovy.json.JsonSlurper()

        val mixinFile = File(resourcesDir.get().asFile, "biblioteca.client.mixins.json")
        @Suppress("UNCHECKED_CAST")
        val config = slurper.parse(mixinFile) as MutableMap<String, Any?>
        config["client"] = (config["client"] as List<*>).filterNot { it in noRenderMixins }
        // processResources runs before Loom adds the refmap entry, and this
        // rewrite replaces the remapped config wholesale. Put it back for the
        // obfuscated nodes or string mixin targets, such as the private
        // CreateWorldScreen$MoreTab, cannot be resolved at runtime.
        if (sc.current.parsed < "26.1") {
            config["refmap"] = "biblioteca.refmap.json"
        }
        File(out, mixinFile.name).writeText(
            groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(config)) + "\n")

        val metadataFile = File(resourcesDir.get().asFile, "fabric.mod.json")
        @Suppress("UNCHECKED_CAST")
        val metadata = slurper.parse(metadataFile) as MutableMap<String, Any?>
        metadata["version"] = metadata["version"].toString() + "-norender"
        metadata["name"] = metadata["name"].toString() + " (No Render)"
        File(out, metadataFile.name).writeText(
            groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(metadata)) + "\n")
    }
}

val noRenderJar = tasks.register("noRenderJar") {
    group = "build"
    description = "Assembles the no-render variant jar from the normal jar."
    // Cut from the deliverable (remapped) jar, not the dev jar. Loom's
    // remapJar is the shipped artifact on obfuscated nodes; 26.1+ has no remap
    // step and jar is already the deliverable.
    val deliverable = if (sc.current.parsed < "26.1") {
        tasks.named("remapJar").flatMap {
            (it as org.gradle.api.tasks.bundling.AbstractArchiveTask).archiveFile
        }
    } else {
        tasks.jar.flatMap { it.archiveFile }
    }
    dependsOn(
        if (sc.current.parsed < "26.1") tasks.named("remapJar") else tasks.jar,
        prepareNoRenderResources
    )

    val primaryJar = deliverable
    val variantJar = primaryJar.map { primary ->
        File(primary.asFile.parentFile, primary.asFile.name.removeSuffix(".jar") + "-norender.jar")
    }
    inputs.file(primaryJar)
    inputs.dir(noRenderDir)
    outputs.file(variantJar)
    // The variant is cut from the remapped jar; rerun after every build rather
    // than risk a stale config without the refmap entry.
    outputs.upToDateWhen { false }

    doLast {
        val source = primaryJar.get().asFile
        val target = variantJar.get()
        // Read the finished primary jar at execution time: its mixin config has
        // the refmap entry Loom adds during remap, which the processed resources
        // do not contain yet.
        val droppedClasses = noRenderMixins.map { "babel/biblioteca/client/mixin/$it.class" }.toSet()
        val droppedNested = noRenderMixins.map { "babel/biblioteca/client/mixin/$it\$" }
        val rewritten = setOf("fabric.mod.json", "biblioteca.client.mixins.json")

        target.parentFile.mkdirs()
        ZipFile(source).use { zip ->
            ZipOutputStream(
                BufferedOutputStream(FileOutputStream(target))).use { out ->
                for (entry in zip.entries()) {
                    val name = entry.name
                    if (name in rewritten) continue
                    if (name in droppedClasses || droppedNested.any { name.startsWith(it) }) continue
                    val copy = ZipEntry(name)
                    copy.time = entry.time
                    out.putNextEntry(copy)
                    if (!entry.isDirectory) {
                        zip.getInputStream(entry).use { input -> input.copyTo(out) }
                    }
                    out.closeEntry()
                }
                for (file in (noRenderDir.get().asFile.listFiles() ?: emptyArray())) {
                    if (!file.isFile) continue
                    val copy = ZipEntry(file.name)
                    copy.time = file.lastModified()
                    out.putNextEntry(copy)
                    out.write(file.readBytes())
                    out.closeEntry()
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Grouped jars
// ---------------------------------------------------------------------------
// A group is compiled once, at its representative release, and published with a
// Minecraft dependency range instead of one exact version. The intermediate jar
// in versions/<node>/build/libs stays untouched for debugging.
if (project.path != ":") {
    @Suppress("UNCHECKED_CAST")
    val jarGroups = rootProject.extensions.extraProperties
        .get("bibliotecaJarGroups") as List<Map<String, Any?>>
    val fullRendererVersion = "26.3"
    val currentGroup = jarGroups.firstOrNull { it["representative"] == sc.current.version }
    val isFullRenderer = sc.current.version == fullRendererVersion

    if (currentGroup != null || isFullRenderer) {
        val artifactId = if (isFullRenderer) {
            fullRendererVersion
        } else {
            currentGroup!!["id"] as String
        }
        val minecraftRange = if (isFullRenderer) {
            fullRendererVersion
        } else {
            currentGroup!!["minecraft"] as String
        }
        val useNoRender = !isFullRenderer && currentGroup!!["noRender"] == true
        val sourceTask = when {
            useNoRender -> "noRenderJar"
            sc.current.parsed < "26.1" -> "remapJar"
            else -> "jar"
        }
        val sourceJar = if (useNoRender) {
            // noRenderJar is a plain task that writes a sibling of the remapped
            // jar, so it has no archiveFile property to read.
            layout.buildDirectory.file(
                "libs/${property("mod.id")}-${project.version}-norender.jar"
            )
        } else {
            tasks.named(sourceTask).flatMap {
                (it as org.gradle.api.tasks.bundling.AbstractArchiveTask).archiveFile
            }
        }
        val modVersion = property("mod.version").toString()
        val targetJar = rootProject.layout.buildDirectory.file(
            "grouped/biblioteca-$modVersion+$artifactId" +
                if (useNoRender) "-norender.jar" else ".jar"
        )

        val groupJar = tasks.register("groupJar") {
            group = "build"
            description = "Writes the grouped jar for $artifactId."
            dependsOn(sourceTask)
            inputs.file(sourceJar)
            outputs.file(targetJar)
            // Metadata injection is cheap; always rewrite instead of risking a
            // stale range after a property change.
            outputs.upToDateWhen { false }

            doLast {
                val source = sourceJar.get().asFile
                val target = targetJar.get().asFile
                target.parentFile.mkdirs()

                val metadata = ZipFile(source).use { zip ->
                    val entry = zip.getEntry("fabric.mod.json")
                        ?: error("$source has no fabric.mod.json")
                    val text = zip.getInputStream(entry).use {
                        it.readBytes().toString(Charsets.UTF_8)
                    }
                    @Suppress("UNCHECKED_CAST")
                    val json = groovy.json.JsonSlurper().parseText(text) as MutableMap<String, Any?>
                    json["version"] = "$modVersion+$artifactId" +
                        if (useNoRender) "-norender" else ""
                    @Suppress("UNCHECKED_CAST")
                    val depends = json["depends"] as MutableMap<String, Any?>
                    depends["minecraft"] = minecraftRange
                    if (useNoRender) {
                        json["name"] = json["name"].toString().removeSuffix(" (No Render)") + " (No Render)"
                    }
                    groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(json)) + "\n"
                }

                ZipFile(source).use { zip ->
                    ZipOutputStream(
                        BufferedOutputStream(FileOutputStream(target))
                    ).use { out ->
                        for (entry in zip.entries()) {
                            if (entry.name == "fabric.mod.json") continue
                            val copy = ZipEntry(entry.name)
                            copy.time = entry.time
                            out.putNextEntry(copy)
                            if (!entry.isDirectory) {
                                zip.getInputStream(entry).use { input -> input.copyTo(out) }
                            }
                            out.closeEntry()
                        }
                        val bytes = metadata.toByteArray(Charsets.UTF_8)
                        val copy = ZipEntry("fabric.mod.json")
                        copy.time = source.lastModified()
                        out.putNextEntry(copy)
                        out.write(bytes)
                        out.closeEntry()
                    }
                }
            }
        }

        tasks.findByName("build")?.finalizedBy(groupJar)
    }
}

tasks.assemble {
    dependsOn(noRenderJar)
}