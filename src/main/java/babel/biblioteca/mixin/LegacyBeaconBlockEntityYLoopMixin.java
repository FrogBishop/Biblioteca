package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeaconBeamBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Pre-1.21.5 version of BeaconBlockEntityYLoopMixin.
 *
 * The algorithm is identical, but this era changed the BeamSection colour from
 * float[] to int at 1.21 while keeping the class and method names. The grouped
 * 1.20.5 - 1.21.1 jar spans that change, so the colour access goes through
 * reflection; the section constructor, getColor and increaseHeight are selected
 * once at class load.
 */
@Mixin(BeaconBlockEntity.class)
public abstract class LegacyBeaconBlockEntityYLoopMixin {

    private static final Constructor<?> SECTION_CONSTRUCTOR;
    private static final Method SECTION_GET_COLOR;
    private static final Method DYE_TEXTURE_COLORS;
    private static final Method DYE_TEXTURE_COLOR;

    static {
        Constructor<?> constructor = null;
        for (Constructor<?> candidate : BeaconBlockEntity.BeaconBeamSection.class.getConstructors()) {
            if (candidate.getParameterCount() == 1) {
                constructor = candidate;
                break;
            }
        }
        if (constructor == null) {
            throw new IllegalStateException("No BeamSection colour constructor");
        }
        SECTION_CONSTRUCTOR = constructor;
        try {
            SECTION_GET_COLOR = BeaconBlockEntity.BeaconBeamSection.class.getMethod("getColor");
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException("No BeamSection.getColor", exception);
        }
        DYE_TEXTURE_COLORS = methodOrNull(DyeColor.class, "getTextureDiffuseColors");
        DYE_TEXTURE_COLOR = methodOrNull(DyeColor.class, "getTextureDiffuseColor");
    }

    private static Method methodOrNull(Class<?> owner, String name) {
        try {
            return owner.getMethod(name);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    @WrapOperation(
            method = "updateBase",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getMinY()I"))
    private static int biblioteca$noFloor(Level level, Operation<Integer> original) {
        return LoopingRegistry.isLooping(level) ? Integer.MIN_VALUE : original.call(level);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private static void biblioteca$ringBeam(
            Level level, BlockPos pos, BlockState selfState, BeaconBlockEntity entity, CallbackInfo ci) {

        if (!LoopingRegistry.isLooping(level)) return;

        LegacyBeaconBlockEntityAccessor accessor = (LegacyBeaconBlockEntityAccessor) entity;

        if (!LoopingRegistry.beaconsIgnoreSky(level)) {
            if (!entity.getBeamSections().isEmpty()) {
                accessor.biblioteca$setBeamSections(new ArrayList<>());
            }
            accessor.biblioteca$setLevels(0);
            return;
        }

        if (level.getGameTime() % 10L != 0L) return;

        accessor.biblioteca$setBeamSections(biblioteca$scanAroundTheRing(level, pos));
    }

    private static List<BeaconBlockEntity.BeaconBeamSection> biblioteca$scanAroundTheRing(
            Level level, BlockPos origin) {

        int period = level.getHeight();
        int minY = level.getMinY();
        int x = origin.getX();
        int z = origin.getZ();

        List<BeaconBlockEntity.BeaconBeamSection> sections = new ArrayList<>(4);
        BeaconBlockEntity.BeaconBeamSection lastSection = null;

        for (int step = 0; step < period; step++) {
            int sectionY = Math.floorMod(origin.getY() + step - minY, period) + minY;
            BlockPos probe = new BlockPos(x, sectionY, z);
            BlockState state = level.getBlockState(probe);

            if (state.getBlock() instanceof BeaconBeamBlock beamBlock) {
                if (sections.size() <= 1) {
                    lastSection = biblioteca$newSection(beamBlock);
                    sections.add(lastSection);
                } else if (lastSection != null) {
                    if (biblioteca$sameColor(lastSection, beamBlock)) {
                        ((LegacyBeaconBeamSectionAccessor) lastSection).biblioteca$increaseHeight();
                    } else {
                        lastSection = biblioteca$average(lastSection, beamBlock);
                        sections.add(lastSection);
                    }
                }
            } else {
                if (lastSection == null
                        || biblioteca$blocksBeam(state, level, probe) && !state.is(Blocks.BEDROCK)) {
                    break;
                }
                ((LegacyBeaconBeamSectionAccessor) lastSection).biblioteca$increaseHeight();
            }
        }

        return sections;
    }

    private static boolean biblioteca$blocksBeam(BlockState state, Level level, BlockPos pos) {
        //? if <1.21.2 {
        return state.getLightBlock(level, pos) >= 15;
        //?} else {
        /*return state.getLightBlock() >= 15;
        *///?}
    }

    private static Object biblioteca$textureColor(BeaconBeamBlock block) {
        try {
            return SECTION_CONSTRUCTOR.getParameterTypes()[0] == float[].class
                    ? DYE_TEXTURE_COLORS.invoke(block.getColor())
                    : DYE_TEXTURE_COLOR.invoke(block.getColor());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot read a beacon colour", exception);
        }
    }

    private static BeaconBlockEntity.BeaconBeamSection biblioteca$newSection(BeaconBeamBlock block) {
        try {
            return (BeaconBlockEntity.BeaconBeamSection) SECTION_CONSTRUCTOR.newInstance(
                    biblioteca$textureColor(block));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot create a beam section", exception);
        }
    }

    private static boolean biblioteca$sameColor(
            BeaconBlockEntity.BeaconBeamSection section, BeaconBeamBlock block) {
        try {
            Object current = SECTION_GET_COLOR.invoke(section);
            Object next = biblioteca$textureColor(block);
            if (current instanceof float[] currentFloats && next instanceof float[] nextFloats) {
                return Arrays.equals(currentFloats, nextFloats);
            }
            return current.equals(next);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot compare beacon colours", exception);
        }
    }

    private static BeaconBlockEntity.BeaconBeamSection biblioteca$average(
            BeaconBlockEntity.BeaconBeamSection section, BeaconBeamBlock block) {
        try {
            Object current = SECTION_GET_COLOR.invoke(section);
            Object next = biblioteca$textureColor(block);
            Object average;
            if (current instanceof float[] a && next instanceof float[] b) {
                average = new float[] {
                        (a[0] + b[0]) * 0.5F,
                        (a[1] + b[1]) * 0.5F,
                        (a[2] + b[2]) * 0.5F,
                };
            } else {
                int a = (Integer) current;
                int b = (Integer) next;
                int r = (((a >> 16) & 0xFF) + ((b >> 16) & 0xFF)) / 2;
                int g = (((a >> 8) & 0xFF) + ((b >> 8) & 0xFF)) / 2;
                int blue = ((a & 0xFF) + (b & 0xFF)) / 2;
                average = 0xFF000000 | r << 16 | g << 8 | blue;
            }
            return (BeaconBlockEntity.BeaconBeamSection) SECTION_CONSTRUCTOR.newInstance(average);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot average beacon colours", exception);
        }
    }
}
