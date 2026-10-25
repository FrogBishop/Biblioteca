package babel.biblioteca;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.commands.CommandSourceStack;
import com.mojang.serialization.Codec;
import java.util.function.Supplier;
//? if >=1.19.3 {
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
//?} else {
/*
*///?}

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * The handful of names that moved between the releases this mod targets.
 *
 * Stonecutter rewrites {@code Identifier} to {@code ResourceLocation} before
 * 1.21.11, so the source keeps the modern spelling and only this helper has to
 * know which id factory exists in the active release.
 */
public final class Compat {

    private Compat() {}

    public static Level level(Entity entity) {
        //? if >=1.20 {
        return entity.level();
        //?} else {
        /*return entity.level;
        *///?}
    }

    public static int minY(BlockGetter level) {
        //? if >=1.17 {
        return level.getMinY();
        //?} else {
        /*return 0;
        *///?}
    }

    public static int height(BlockGetter level) {
        //? if >=1.17 {
        return level.getHeight();
        //?} else {
        /*return level.getMaxBuildHeight();
        *///?}
    }

    public static int maxY(BlockGetter level) { return minY(level) + height(level) - 1; }
    public static int minSection(BlockGetter level) { return minY(level) >> 4; }
    public static int maxSection(BlockGetter level) { return maxY(level) >> 4; }
    public static int sectionCount(BlockGetter level) { return height(level) >> 4; }
    public static long chunkKey(int x, int z) { return (x & 0xffffffffL) | ((z & 0xffffffffL) << 32); }
    public static net.minecraft.world.level.ChunkPos chunkPos(long key) { return new net.minecraft.world.level.ChunkPos((int)key, (int)(key >>> 32)); }
    public static int chunkX(net.minecraft.world.level.ChunkPos pos) {
        //? if >=26.1 {
        return pos.x();
        //?} else {
        /*return pos.x;
        *///?}
    }
    public static int chunkZ(net.minecraft.world.level.ChunkPos pos) {
        //? if >=26.1 {
        return pos.z();
        //?} else {
        /*return pos.z;
        *///?}
    }
    public static long chunkKey(net.minecraft.world.level.ChunkPos pos) { return chunkKey(chunkX(pos), chunkZ(pos)); }
    public static int chunkBlockX(net.minecraft.world.level.ChunkPos pos, int x) { return chunkX(pos) * 16 + x; }
    public static int chunkBlockZ(net.minecraft.world.level.ChunkPos pos, int z) { return chunkZ(pos) * 16 + z; }

    public static BlockPos blockPos(double x, double y, double z) {
        //? if >=1.19.4 {
        return BlockPos.containing(x, y, z);
        //?} else {
        /*return new BlockPos(x, y, z);
        *///?}
    }

    public static BlockPos blockPos(Vec3 point) { return blockPos(point.x, point.y, point.z); }

    public static MutableComponent literal(String text) {
        //? if >=1.19 {
        return Component.literal(text);
        //?} else {
        /*return new net.minecraft.network.chat.TextComponent(text);
        *///?}
    }

    public static MutableComponent translatable(String key, Object... args) {
        //? if >=1.19 {
        return Component.translatable(key, args);
        //?} else {
        /*return new net.minecraft.network.chat.TranslatableComponent(key, args);
        *///?}
    }

    public static void sendSuccess(CommandSourceStack source, Supplier<Component> text, boolean broadcast) {
        //? if >=1.20 {
        source.sendSuccess(text, broadcast);
        //?} else {
        /*source.sendSuccess(text.get(), broadcast);
        *///?}
    }

    public static java.util.List<net.minecraft.world.phys.shapes.VoxelShape> entityCollisions(Level level, Entity entity, AABB area) {
        //? if >=1.18 {
        return level.getEntityCollisions(entity, area);
        //?} else {
        /*return level.getEntityCollisions(entity, area, candidate -> true).toList();
        *///?}
    }

    public static boolean isSolid(net.minecraft.world.level.block.state.BlockState state) {
        //? if >=1.20 {
        return state.isSolid();
        //?} else {
        /*return state.getMaterial().isSolid();
        *///?}
    }

    public static ResourceKey<Level> dimensionKey(Identifier id) {
        //? if >=1.19.3 {
        return ResourceKey.create(Registries.DIMENSION, id);
        //?} else {
        /*return ResourceKey.create(Registry.DIMENSION_REGISTRY, id);
        *///?}
    }

    public static Codec<ResourceKey<Level>> dimensionCodec() {
        return Identifier.CODEC.xmap(Compat::dimensionKey, Compat::keyId);
    }

    public static Registry<Identifier> customStats() {
        //? if >=1.19.3 {
        return BuiltInRegistries.CUSTOM_STAT;
        //?} else {
        /*return Registry.CUSTOM_STAT;
        *///?}
    }


    /** BlockPos.betweenClosed(AABB) was added after the oldest supported releases. */
    public static Iterable<BlockPos> blocksIn(AABB area) {
        return BlockPos.betweenClosed(blockPos(area.minX, area.minY, area.minZ),
                blockPos(area.maxX, area.maxY, area.maxZ));
    }

    /** A namespaced id, created with the factory that exists in this release. */
    public static Identifier id(String namespace, String path) {
        //? if >=1.21.11 {
        return Identifier.fromNamespaceAndPath(namespace, path);
        //?} else {
        /*return java.util.Objects.requireNonNull(Identifier.tryParse(namespace + ":" + path),
                "Invalid identifier: " + namespace + ":" + path);
        *///?}
    }

    /** Parses an id written in a packet or a save file. */
    public static Identifier parseId(String value) {
        //? if >=1.21.11 {
        return Identifier.parse(value);
        //?} else {
        /*return java.util.Objects.requireNonNull(Identifier.tryParse(value),
                "Invalid identifier: " + value);
        *///?}
    }

    /** The id of a dimension key (location() before 1.21.11). */
    public static Identifier keyId(ResourceKey<Level> key) {
        //? if >=1.21.11 {
        return key.identifier();
        //?} else {
        /*return key.location();
        *///?}
    }
}
