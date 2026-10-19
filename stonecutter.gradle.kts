plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.3"

stonecutter parameters {
    // Most of the 1.20.4 - 1.21.10 renames are mechanical; keeping them here
    // keeps the shared sources readable and avoids a conditional around every
    // import.
    replacements {
        string(current.parsed < "1.21.11") {
            replace("Identifier", "ResourceLocation")
            replace(".identifier()", ".location()")
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

        string(current.parsed < "26.1") {
            replace("SpeleothemBlock", "PointedDripstoneBlock")
        }
    }
}
