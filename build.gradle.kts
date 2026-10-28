import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.net.URI
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.gradle.api.tasks.bundling.AbstractArchiveTask

plugins {
    // Applies the remapping Loom variant for obfuscated versions and the plain
    // Loom variant for 26.1+, where Minecraft ships unobfuscated.
    id("dev.kikugie.loom-back-compat")
}

version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = property("mod.id") as String

val accessWidener = when {
    sc.current.parsed >= "26.3" -> "biblioteca.accesswidener"
    sc.current.parsed < "1.17" -> "biblioteca-classic116.accesswidener"
    sc.current.parsed < "1.20.2" -> "biblioteca-classic.accesswidener"
    sc.current.parsed >= "1.21.2" && sc.current.parsed < "1.21.9" -> "biblioteca-weather.accesswidener"
    else -> null
}
if (accessWidener != null) {
    loom { accessWidenerPath = rootProject.file("src/main/resources/$accessWidener") }
}

val requiredJava: JavaVersion = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
    else -> JavaVersion.VERSION_17
}

repositories {
    // Prefer the exact cached intermediary artifact when a cold Maven download stalls.
    if (rootProject.file("local-repo/net/fabricmc/intermediary/${sc.current.version}/intermediary-${sc.current.version}-v2.jar").isFile) {
        exclusiveContent {
            forRepository {
                maven { name = "Cached intermediary"; url = uri(rootProject.file("local-repo")) }
            }
            filter { includeModule("net.fabricmc", "intermediary") }
        }
    }
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

    ZipFile(bundle).use { zip ->
        for (entry in zip.entries()) {
            val name = entry.name.substringAfterLast('/')
            if (!entry.name.startsWith("META-INF/jars/") || !entry.name.endsWith(".jar")) continue
            val target = File(cache, name)
            if (!target.isFile) target.outputStream().use { output ->
                zip.getInputStream(entry).use { input -> input.copyTo(output) }
            }
        }
    }
    val jars = cache.listFiles { file -> file.isFile && file.extension == "jar"
            && wanted.any { file.name.startsWith(it) } }?.sortedBy { it.name }.orEmpty()
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
    val compileModules = fabricApiModules(fabricApiVersion, fabricApiUrl)
    for (jar in compileModules) modImplementation(files(jar))
    val apiCache = rootProject.file(".gradle/fabric-api/$fabricApiVersion")
    for (jar in apiCache.listFiles { file -> file.extension == "jar" && file.name != "fabric-api.jar" }.orEmpty()) {
        if (jar !in compileModules) modRuntimeOnly(files(jar))
    }
    // Loom prepares the complete runtime bundle, including nested modules, in the node's mappings.
    // This also makes runClient/runServer satisfy the dependency on the Fabric API umbrella mod.
    modRuntimeOnly(files(rootProject.file(".gradle/fabric-api/$fabricApiVersion/fabric-api.jar")))
}

java {
    sourceCompatibility = requiredJava
    targetCompatibility = requiredJava
}

// Hooks whose 26.3 target does not exist in an older release. The matching
// legacy or mid-era variant is listed in the same config; this set only removes
// the inapplicable one. The generated mixin config is filtered to match, because
// Mixin would otherwise fail to load the class.
@Suppress("UNCHECKED_CAST")
val sources263 = groovy.json.JsonSlurper().parse(rootProject.file("gradle/26.3-sources.json")) as Map<String, List<String>>
val excludedServerMixins = if (sc.current.version == "26.3") {
    rootProject.fileTree("src/main/java/babel/biblioteca/mixin").matching { include("*.java") }
        .files.map { it.nameWithoutExtension }.toSet() - sources263.getValue("mixins").toSet()
} else buildSet {
    if (sc.current.parsed < "1.20") add("SkyLightEngineYLoopMixin")
    else addAll(listOf("LegacySkyLightEngineYLoopMixin", "LegacyLightStorageYLoopMixin"))
    if (sc.current.parsed < "1.21.6") add("LeashableYLoopMixin")
    if (sc.current.parsed < "1.21" || sc.current.parsed >= "1.21.6") add("LegacyLeashSpringYLoopMixin")
    if (sc.current.parsed < "1.20.3") add("BreezeAimYLoopMixin")
    if (sc.current.parsed < "1.21.9") add("ProjectileOwnerYLoopMixin")
    if (sc.current.parsed < "1.20.5") add("CrossbowAimYLoopMixin") else add("LegacyCrossbowAimYLoopMixin")
    if (sc.current.parsed >= "1.21.2") add("LegacyEndermanGazeYLoopMixin")
    if (sc.current.parsed < "1.20.5") add("WalkNodeEvaluatorYLoopMixin") else add("LegacyWalkNodeEvaluatorYLoopMixin")
    if (sc.current.parsed >= "1.19.3") add("LegacyServerSoundsMixin")
    if (sc.current.parsed < "1.17") add("DripstoneYLoopMixin")
    if (sc.current.parsed < "1.19") add("NeighborUpdaterYLoopMixin") else add("LegacyNeighborYLoopMixin")
    if (sc.current.parsed < "1.20.2") add("MobMeleeRangeYLoopMixin") else add("LegacyLivingInterpolationYLoopMixin")

    if (sc.current.parsed < "26.3") add("LinearInterpolationYLoopMixin")
    if (sc.current.parsed < "1.19.4" || sc.current.parsed >= "1.20.5") add("LegacyEntityReachMixin")
    if (sc.current.parsed < "1.21.5" || sc.current.parsed >= "26.3") add("InterpolationHandlerYLoopMixin")
    if (sc.current.parsed < "1.21.11") add("AttackRangeYLoopMixin")
    if (sc.current.parsed < "1.20.5") add("PlayerEntityInteractionMixin")
    if (sc.current.parsed < "26.1") addAll(listOf("PlayerInteractionLocationYLoopMixin", "SaplingBlockYLoopMixin"))
    if (sc.current.parsed >= "1.21" && sc.current.parsed < "1.21.9") add("ItemFrameYLoopMixin")
    if (sc.current.parsed < "1.20.5") add("ServerPlayerGameModeMixin")
    if (sc.current.parsed >= "1.20.5") add("LegacyInlineBlockReachMixin")
    // Player.canInteractWithBlock exists from 1.20.5 through 1.21.10; outside
    // that window the reach check has a different shape and the hook has no
    // target method to attach to.
    if (sc.current.parsed < "1.20.5" || sc.current.parsed >= "1.21.11") add("LegacyBlockReachMixin")
    // Before 26.1 the dedicated build-limit message helper does not exist yet.
    if (sc.current.parsed < "26.1") add("ServerPlayerBuildLimitMixin")
    if (sc.current.parsed < "1.21.11") {
        // Player.isWithinBlockInteractionRange does not exist yet; the reach
        // check is Player.canInteractWithBlock, which LegacyBlockReachMixin
        // folds instead.
        add("PlayerReachMixin")
    }
    if (sc.current.parsed >= "1.20.5") {
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
    if (sc.current.parsed < "26.3") {
        addAll(listOf(
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

// A single selection drives both compilation and runtime registration. No absent
// renderer hook is hidden by require=0, and unselected implementations are not packaged.
val clientCoreMixins = if (sc.current.version == "26.3") sources263.getValue("clientCore").toSet() else buildSet {
    // Earlier subtitles have no audibility gate; the sound engine already gates their events.
    if (sc.current.parsed >= "1.20.3") add("SubtitleDistanceYLoopMixin")
    if (sc.current.parsed >= "1.19.4") addAll(listOf("CreateWorldScreenMoreTabMixin", "GridLayoutTabAccessor"))
    else add("LegacyCreateWorldScreenMixin")
    addAll(listOf(
    "ClientLevelDirtyMixin",
    "ProjectileUtilYLoopMixin",
    "OptionsSubScreenAccessor", "VideoSettingsScreenMixin",
    "ClientLevelSoundYLoopMixin", "SoundEngineYLoopMixin",
    "SubtitleDirectionYLoopMixin",
    ))
    if (sc.current.parsed >= "1.21.2") add("ClientPacketListenerYLoopMixin")
    if (sc.current.parsed < "26.1") add("LegacyInteractionLocationYLoopMixin")
}
val selectedClientMixins = if (sc.current.version == "26.3") sources263.getValue("client").toSet() else buildSet {
    addAll(clientCoreMixins)
    if (sc.current.parsed < "1.21.2") add("LegacyLeashRenderYLoopMixin")
    else if (sc.current.parsed < "1.21.9") add("LeashRenderStateYLoopMixin")
    add("EntityRenderDispatcherYLoopMixin")
    if (sc.current.parsed >= "1.21.6") add("TrackedWaypointYLoopMixin")
    addAll(listOf("FrustumInvoker", "FrustumYLoopMixin", "EntityShouldRenderYLoopMixin",
            "ParticleYLoopMixin", "ClientHorizonYLoopMixin", "ViewAreaAccessor"))
    if (sc.current.parsed >= "1.21.6") add("VoidFogMixin") else add("LegacyVoidFogMixin")
    if (sc.current.parsed >= "1.21.2") add("CloudRendererLoopMixin") else add("LegacyClouds1211Mixin")
    add("RainImpactsYLoopMixin")
    add(if (sc.current.parsed >= "1.20.2") "SectionOcclusionGraphLoopMixin" else "LegacyTerrainVisibilityMixin")
    add(if (sc.current.parsed >= "1.21.2") "WeatherEffectRendererYLoopMixin" else "ClassicWeatherYLoopMixin")

    if (sc.current.parsed < "1.21.5") {
        add("LegacySectionBufferMixin")
        add(if (sc.current.parsed >= "1.19") "LegacyVertexBufferMixin" else "BufferedVertexYLoopMixin")
        add(if (sc.current.parsed >= "1.17") "ClassicTerrainYLoopMixin" else "FixedTerrainYLoopMixin")
    }
    if (sc.current.version == "1.21.5") addAll(listOf("GpuLayerTerrainYLoopMixin", "GpuCompiledSectionAccessor"))
    if (sc.current.parsed >= "1.21.6" && sc.current.parsed < "26.3") add("GpuTerrainYLoopMixin")
    if (sc.current.parsed < "1.21.9") {
        add("LegacyBlockEntityRenderYLoopMixin")
        add(if (sc.current.parsed < "1.21.2") "LegacyEntityRender1211YLoopMixin" else "LegacyEntityRender1218YLoopMixin")
    }
    add("BlockEntityRenderRangeYLoopMixin")
    add(when {
        sc.current.parsed >= "1.21.6" -> "RenderRegionCacheYLoopMixin"
        sc.current.parsed >= "1.18" -> "LegacyRenderChunkYLoopMixin"
        else -> "LegacyRegionSnapshotMixin"
    })
    if (sc.current.parsed >= "1.21.9") addAll(listOf(
        "EntityRenderYLoopMixin", "BlockEntityPositionYLoopMixin", "BeaconBeamRenderYLoopMixin",
    )) else add("LegacyBeaconBeamYLoopMixin")
    if (sc.current.parsed >= "26.3") add("LevelRendererYLoopMixin")
}
val allClientMixins = rootProject.fileTree("src/main/java/babel/biblioteca/client/mixin")
    .matching { include("*.java") }.files.map { it.nameWithoutExtension }.toSet()
sourceSets.named("main") {
    if (sc.current.parsed < "26.3") java.exclude("babel/biblioteca/client/LoopingConfigScreen.java")
    else java.exclude("babel/biblioteca/client/LegacyDimensionConfigScreen.java")
    java.exclude((allClientMixins - selectedClientMixins).map { "babel/biblioteca/client/mixin/$it.java" })
    if (sc.current.parsed >= "1.21.5" || sc.current.parsed < "1.17") java.exclude("babel/biblioteca/client/ClassicTerrainBackend.java")
    if (sc.current.parsed >= "1.21.5") java.exclude("babel/biblioteca/client/LegacyVertexData.java")
    if (sc.current.parsed >= "1.17") java.exclude("babel/biblioteca/client/FixedTerrainBackend.java")
    if (sc.current.parsed < "26.3") java.exclude("babel/biblioteca/client/LoopingTranslucency.java")
    if (sc.current.parsed < "1.21.5" || sc.current.parsed >= "26.3") java.exclude("babel/biblioteca/client/GpuTerrainIndices.java")
    if (sc.current.parsed < "1.21.5" || sc.current.parsed >= "26.3") java.exclude(
        "babel/biblioteca/client/GpuTerrainBuffer.java", "babel/biblioteca/client/GpuTerrainDraws.java")
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
    val apiVersion = sc.properties["deps.fabric_api"] as String
    val apiBundle = rootProject.file(".gradle/fabric-api/$apiVersion/fabric-api.jar")
    val apiId = ZipFile(apiBundle).use { zip ->
        val json = zip.getInputStream(zip.getEntry("fabric.mod.json")).bufferedReader().use { it.readText() }
        (groovy.json.JsonSlurper().parseText(json) as Map<*, *>)["id"] as String
    }
    val props = mapOf(
        "fabric_api_id" to apiId,
        "version" to project.version.toString(),
        "minecraft" to (sc.properties["mod.mc_compat"] as String),
        "loader" to (project.property("deps.fabric_loader") as String),
        "java" to requiredJava.majorVersion,
    )
    inputs.properties(props)
    inputs.property("excludedServerMixins", excludedServerMixins.joinToString(","))
    inputs.property("selectedClientMixins", selectedClientMixins.sorted().joinToString(","))
    filesMatching("fabric.mod.json") { expand(props) }
    filesMatching("*.mixins.json") { expand("java" to "JAVA_${requiredJava.majorVersion}") }
    listOf("biblioteca.accesswidener", "biblioteca-weather.accesswidener", "biblioteca-classic.accesswidener", "biblioteca-classic116.accesswidener").filter { it != accessWidener }.forEach { exclude(it) }

    doLast {
        val outputDir = destinationDirectory.get().asFile
        if (accessWidener != null) {
            val metadataFile = outputDir.resolve("fabric.mod.json")
            @Suppress("UNCHECKED_CAST")
            val metadata = groovy.json.JsonSlurper().parse(metadataFile) as MutableMap<String, Any?>
            metadata["accessWidener"] = accessWidener
            metadataFile.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(metadata)) + "\n")
        }

        if (excludedServerMixins.isNotEmpty()) {
            val configFile = outputDir.resolve("biblioteca.mixins.json")
            val slurper = groovy.json.JsonSlurper()
            @Suppress("UNCHECKED_CAST")
            val config = slurper.parse(configFile) as MutableMap<String, Any?>
            config["mixins"] = (config["mixins"] as List<*>).filterNot { it in excludedServerMixins }
            config["injectors"] = mapOf("defaultRequire" to 1)
            configFile.writeText(
                groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(config)) + "\n")
        }

        val clientConfig = mutableMapOf<String, Any?>(
            "required" to true,
            "package" to "babel.biblioteca.client.mixin",
            "compatibilityLevel" to "JAVA_${requiredJava.majorVersion}",
            "client" to selectedClientMixins.sorted(),
            "injectors" to mapOf("defaultRequire" to 1),
        )
        outputDir.resolve("biblioteca.client.mixins.json").writeText(
            groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(clientConfig)) + "\n")
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
val noRenderMixins = selectedClientMixins - clientCoreMixins

val noRenderDir = layout.buildDirectory.dir("generated/noRender")

val prepareNoRenderResources = tasks.register("prepareNoRenderResources") {
    group = "build"
    description = "Rewrites the client mixin config and mod metadata for the no-render jar."
    dependsOn(tasks.processResources)
    val resourcesDir = layout.buildDirectory.dir("resources/main")
    inputs.dir(resourcesDir)
    inputs.property("removedMixins", noRenderMixins)
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

// Exact-version distribution artifacts. Metadata is never widened across unverified ABIs.
val collectArtifacts = tasks.register<Copy>("collectArtifacts") {
    group = "build"
    description = "Collects this node's full and no-render artifacts."
    val deliverable = if (sc.current.parsed < "26.1") {
        tasks.named("remapJar").flatMap { (it as AbstractArchiveTask).archiveFile }
    } else {
        tasks.jar.flatMap { it.archiveFile }
    }
    dependsOn(noRenderJar)
    from(deliverable)
    from(noRenderJar)
    into(rootProject.layout.buildDirectory.dir("distributions"))
}
tasks.named("build") { finalizedBy(collectArtifacts) }

// Used by tools/verify_matrix.py to launch the real Fabric transformer without a game window.
tasks.register("writeValidationConfig") {
    group = "verification"
    dependsOn(tasks.classes)
    dependsOn(tasks.matching { it.name == "configureLaunch" })
    dependsOn(tasks.matching { it.name == "extractNatives" })
    val report = rootProject.layout.buildDirectory.file("validation/runtime/${sc.current.version}.json")
    outputs.file(report)
    outputs.upToDateWhen { false }
    doLast {
        val clientRun = tasks.named("runClient").get() as JavaExec
        val serverRun = tasks.named("runServer").get() as JavaExec
        val fabricApiVersion = sc.properties["deps.fabric_api"] as String
        val bundle = rootProject.file(".gradle/fabric-api/$fabricApiVersion/fabric-api.jar")
        val apiModules = bundle.parentFile.listFiles { file -> file.extension == "jar" && file.name != "fabric-api.jar" }
            .orEmpty().mapNotNull { jar ->
                ZipFile(jar).use { zip ->
                    val metadata = zip.getEntry("fabric.mod.json") ?: return@use null
                    val json = zip.getInputStream(metadata).bufferedReader().use { it.readText() }
                    val mod = groovy.json.JsonSlurper().parseText(json) as Map<*, *>
                    mapOf("id" to mod["id"], "environment" to (mod["environment"] ?: "*"))
                }
            }
        val data = mapOf(
            "fabricApiModules" to apiModules,
            "minecraft" to sc.current.version,
            "java" to requiredJava.majorVersion,
            "classpath" to clientRun.classpath.files.map { it.absolutePath },
            "serverClasspath" to serverRun.classpath.files.map { it.absolutePath },
            "clientJvmArgs" to (clientRun.allJvmArgs + clientRun.jvmArgumentProviders.flatMap { it.asArguments() }),
            "serverJvmArgs" to (serverRun.allJvmArgs + serverRun.jvmArgumentProviders.flatMap { it.asArguments() }),
            "resources" to layout.buildDirectory.dir("resources/main").get().asFile.absolutePath,
            "classes" to layout.buildDirectory.dir("classes/java/main").get().asFile.absolutePath,
            "fabricApi" to bundle.absolutePath,
            "fabricApiOnClasspath" to true,
        )
        val file = report.get().asFile
        file.parentFile.mkdirs()
        file.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(data)))
    }
}

tasks.assemble {
    dependsOn(noRenderJar)
}

// Export the exact sources selected and preprocessed by the Fabric version node. Independent
// NeoForge targets consume this tree rather than copying 26.3 sources or parsing directives again.
tasks.register<Sync>("exportNeoForgeSources") {
    group = "build"
    description = "Exports this node's preprocessed shared sources/resources for NeoForge."
    dependsOn(tasks.classes)
    val output = rootProject.layout.buildDirectory.dir("neoforge-shared/${sc.current.version}")
    into(output)
    from(sourceSets.main.get().java) {
        into("java")
        exclude("babel/biblioteca/fabric/**", "babel/biblioteca/client/fabric/**")
    }
    from(tasks.processResources.flatMap { it.destinationDirectory }) {
        into("resources")
        exclude("fabric.mod.json", "*.accesswidener")
    }
    inputs.property("minecraft", sc.current.version)
    inputs.property("clientCoreMixins", clientCoreMixins.sorted())
    doLast {
        val common = groovy.json.JsonSlurper().parse(output.get().file("resources/biblioteca.mixins.json").asFile) as Map<*, *>
        val selection = mapOf(
            "minecraft" to sc.current.version,
            "mixins" to common["mixins"],
            "client" to selectedClientMixins.sorted(),
            "clientCore" to clientCoreMixins.sorted(),
            "excludedClientClasses" to emptyList<String>(),
        )
        output.get().file("selection.json").asFile.writeText(
            groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(selection)) + "\n")
    }
}
