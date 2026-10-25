plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.3"

stonecutter parameters {
    // Most of the 1.20.4 - 1.21.10 renames are mechanical; keeping them here
    // keeps the shared sources readable and avoids a conditional around every
    // import.
    replacements {
        string(current.parsed < "1.20.2") {
            replace("SectionRenderDispatcher.RenderSection", "ChunkRenderDispatcher.RenderChunk")
            replace("SectionRenderDispatcher\$RenderSection", "ChunkRenderDispatcher\$RenderChunk")
            replace("net.minecraft.client.renderer.chunk.SectionRenderDispatcher;", "net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;")
            replace("getRenderSectionAt", "getRenderChunkAt")
            replace("renderSectionLayer", "renderChunkLayer")
        }
        string(current.parsed < "1.19") {
            replace("net.minecraft.util.RandomSource", "java.util.Random")
            replace("RandomSource.create(", "new Random(")
            replace("RandomSource random", "Random random")
        }

        string(current.parsed < "1.21") {
            replace("client.gui.screens.options.OptionsSubScreen", "client.gui.screens.OptionsSubScreen")
            replace("client.gui.screens.options.VideoSettingsScreen", "client.gui.screens.VideoSettingsScreen")
            replace("client/gui/screens/options/OptionsSubScreen", "client/gui/screens/OptionsSubScreen")
            replace("client/gui/screens/options/VideoSettingsScreen", "client/gui/screens/VideoSettingsScreen")
        }
        string(current.parsed < "1.21.11") {
            replace("Identifier", "ResourceLocation")
            replace(".identifier()", ".location()")
            replace("world.entity.animal.golem.SnowGolem", "world.entity.animal.SnowGolem")
            replace("world/entity/animal/golem/SnowGolem", "world/entity/animal/SnowGolem")
            replace("world.entity.animal.equine.Llama", "world.entity.animal.horse.Llama")
            replace("world.entity.monster.skeleton.AbstractSkeleton", "world.entity.monster.AbstractSkeleton")
            replace("world.entity.monster.zombie.Drowned", "world.entity.monster.Drowned")
            replace("world.entity.monster.illager.Illusioner", "world.entity.monster.Illusioner")
            replace("world.entity.projectile.throwableitemprojectile.Snowball", "world.entity.projectile.Snowball")
            replace("world/entity/projectile/throwableitemprojectile/Snowball", "world/entity/projectile/Snowball")
        }

        string(current.parsed < "1.21.2") {
            // No leading dot: the same names appear inside mixin target
            // descriptors such as "Lnet/minecraft/world/level/Level;getMaxY()I".
            replace("getMinY()", "getMinBuildHeight()")
            replace("getMaxY()", "getMaxBuildHeight()")
        }

        string(current.parsed < "1.21.11") {
            // The minecart classes moved back under ...vehicle.minecart in
            // 1.21.11 along with the Identifier rename.
            replace("world.entity.vehicle.minecart.AbstractMinecart", "world.entity.vehicle.AbstractMinecart")
        }

        string(current.parsed < "26.2") {
            replace("SpeleothemBlock", "PointedDripstoneBlock")
        }
    }
}
