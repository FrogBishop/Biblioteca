import java.net.URI
import java.util.zip.ZipFile

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
    mavenCentral()
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
    filesMatching("fabric.mod.json") { expand(props) }
    filesMatching("*.mixins.json") { expand("java" to "JAVA_${requiredJava.majorVersion}") }
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
)

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

val noRenderJar = tasks.register<Jar>("noRenderJar") {
    group = "build"
    description = "Assembles the no-render variant jar from the normal jar."
    dependsOn(tasks.jar, prepareNoRenderResources)

    from(tasks.jar.flatMap { it.archiveFile }.map { zipTree(it) }) {
        for (name in noRenderMixins) {
            exclude("babel/biblioteca/client/mixin/$name.class")
            exclude("babel/biblioteca/client/mixin/$name\$*.class")
        }
        exclude("fabric.mod.json", "biblioteca.client.mixins.json")
    }
    from(noRenderDir)
    archiveClassifier.set("norender")
}

tasks.assemble {
    dependsOn(noRenderJar)
}

