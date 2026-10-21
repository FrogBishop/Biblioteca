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
val clientUiAndAudioMixins = setOf(
    "ClientLevelDirtyMixin", "CreateWorldScreenMoreTabMixin", "GridLayoutTabAccessor",
    "OptionsSubScreenAccessor", "VideoSettingsScreenMixin",
    "ClientLevelSoundYLoopMixin", "SoundEngineYLoopMixin",
    "SubtitleDirectionYLoopMixin", "SubtitleDistanceYLoopMixin",
)
val selectedClientMixins = buildSet {
    addAll(clientUiAndAudioMixins)
    addAll(listOf("FrustumInvoker", "FrustumYLoopMixin", "EntityShouldRenderYLoopMixin",
            "ParticleYLoopMixin", "ClientHorizonYLoopMixin", "SectionOcclusionGraphLoopMixin", "ViewAreaAccessor"))
    if (sc.current.parsed >= "1.21.6") add("VoidFogMixin") else add("LegacyVoidFogMixin")
    if (sc.current.parsed >= "1.21.2") add("CloudRendererLoopMixin") else add("LegacyClouds1211Mixin")
    add("RainImpactsYLoopMixin")
    add(if (sc.current.parsed >= "1.21.2") "WeatherEffectRendererYLoopMixin" else "ClassicWeatherYLoopMixin")

    if (sc.current.parsed < "1.21.5") addAll(listOf(
        "ClassicTerrainYLoopMixin", "LegacyVertexBufferMixin", "LegacySectionBufferMixin",
    ))
    if (sc.current.version == "1.21.5") addAll(listOf("GpuLayerTerrainYLoopMixin", "GpuCompiledSectionAccessor"))
    if (sc.current.parsed >= "1.21.6" && sc.current.parsed < "26.3") add("GpuTerrainYLoopMixin")
    if (sc.current.parsed < "1.21.9") {
        add("LegacyBlockEntityRenderYLoopMixin")
        add(if (sc.current.parsed < "1.21.2") "LegacyEntityRender1211YLoopMixin" else "LegacyEntityRender1218YLoopMixin")
    }
    add("BlockEntityRenderRangeYLoopMixin")
    add(if (sc.current.parsed >= "1.21.6") "RenderRegionCacheYLoopMixin" else "LegacyRenderChunkYLoopMixin")
    if (sc.current.parsed >= "1.21.9") addAll(listOf(
        "EntityRenderYLoopMixin", "BlockEntityPositionYLoopMixin", "BeaconBeamRenderYLoopMixin",
    )) else add("LegacyBeaconBeamYLoopMixin")
    if (sc.current.parsed >= "26.3") add("LevelRendererYLoopMixin")
}
val allClientMixins = rootProject.fileTree("src/main/java/babel/biblioteca/client/mixin")
    .matching { include("*.java") }.files.map { it.nameWithoutExtension }.toSet()
sourceSets.named("main") {
    java.exclude((allClientMixins - selectedClientMixins).map { "babel/biblioteca/client/mixin/$it.java" })
    if (sc.current.parsed >= "1.21.5") java.exclude(
        "babel/biblioteca/client/ClassicTerrainBackend.java",
        "babel/biblioteca/client/LegacyVertexData.java",
    )
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
    val props = mapOf(
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
    listOf("biblioteca.accesswidener", "biblioteca-weather.accesswidener").filter { it != accessWidener }.forEach { exclude(it) }

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
val noRenderMixins = selectedClientMixins - clientUiAndAudioMixins

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
    val report = rootProject.layout.buildDirectory.file("validation/runtime/${sc.current.version}.json")
    outputs.file(report)
    outputs.upToDateWhen { false }
    doLast {
        val clientRun = tasks.named("runClient").get() as JavaExec
        val serverRun = tasks.named("runServer").get() as JavaExec
        val fabricApiVersion = sc.properties["deps.fabric_api"] as String
        val bundle = rootProject.file(".gradle/fabric-api/$fabricApiVersion/fabric-api.jar")
        val data = mapOf(
            "minecraft" to sc.current.version,
            "java" to requiredJava.majorVersion,
            "classpath" to clientRun.classpath.files.map { it.absolutePath },
            "serverClasspath" to serverRun.classpath.files.map { it.absolutePath },
            "clientJvmArgs" to (clientRun.allJvmArgs + clientRun.jvmArgumentProviders.flatMap { it.asArguments() }),
            "serverJvmArgs" to (serverRun.allJvmArgs + serverRun.jvmArgumentProviders.flatMap { it.asArguments() }),
            "resources" to layout.buildDirectory.dir("resources/main").get().asFile.absolutePath,
            "classes" to layout.buildDirectory.dir("classes/java/main").get().asFile.absolutePath,
            "fabricApi" to bundle.absolutePath,
        )
        val file = report.get().asFile
        file.parentFile.mkdirs()
        file.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(data)))
    }
}

tasks.assemble {
    dependsOn(noRenderJar)
}
