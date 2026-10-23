package babel.biblioteca.mixin;

import babel.biblioteca.Compat;

import babel.biblioteca.LoopingRegistry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeaconBeamBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The same bounded beacon scan as the modern backend, using the pre-1.21.5 beam section type.
 * Colours changed from float triples to packed integers in 1.21; each artifact uses its native API.
 */
@Mixin(BeaconBlockEntity.class)
public abstract class LegacyBeaconBlockEntityYLoopMixin {

//? if >=1.17 {
    @WrapOperation(
            method = "updateBase",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getMinY()I"))
    private static int biblioteca$noFloor(Level level, Operation<Integer> original) {
        return LoopingRegistry.isLooping(level) ? Integer.MIN_VALUE : original.call(level);
    }


//?} else {
/*    @ModifyVariable(method = "updateBase", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int biblioteca$legacyBaseRow(int y) {
        Level level = ((BeaconBlockEntity) (Object) this).getLevel();
        return LoopingRegistry.isLooping(level) ? y + Compat.height(level) : y;
    }

*///?}
    //? if >=1.17 {
    @Inject(method = "tick", at = @At("TAIL"))
    private static void biblioteca$ringBeam(Level level, BlockPos pos, BlockState state, BeaconBlockEntity entity, CallbackInfo ci) {
        biblioteca$updateBeam(level, pos, entity);
    }
    //?} else {
    /*@Inject(method = "tick", at = @At("TAIL"))
    private void biblioteca$ringBeam(CallbackInfo ci) {
        BeaconBlockEntity entity = (BeaconBlockEntity) (Object) this;
        biblioteca$updateBeam(entity.getLevel(), entity.getBlockPos(), entity);
    }
    *///?}

    private static void biblioteca$updateBeam(Level level, BlockPos pos, BeaconBlockEntity entity) {
        if (!LoopingRegistry.isLooping(level)) return;

        LegacyBeaconBlockEntityAccessor accessor = (LegacyBeaconBlockEntityAccessor) entity;

        if (!LoopingRegistry.beaconsIgnoreSky(level)) {
            if (!accessor.biblioteca$getBeamSections().isEmpty()) {
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

        int period = Compat.height(level);
        int minY = Compat.minY(level);
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

    private static BeaconBlockEntity.BeaconBeamSection biblioteca$newSection(BeaconBeamBlock block) {
        //? if >=1.21 {
        return new BeaconBlockEntity.BeaconBeamSection(block.getColor().getTextureDiffuseColor());
        //?} else {
        /*return new BeaconBlockEntity.BeaconBeamSection(block.getColor().getTextureDiffuseColors());
        *///?}
    }

    private static boolean biblioteca$sameColor(BeaconBlockEntity.BeaconBeamSection section, BeaconBeamBlock block) {
        //? if >=1.21 {
        return ((LegacyBeaconBeamSectionAccessor) section).biblioteca$color() == block.getColor().getTextureDiffuseColor();
        //?} else {
        /*return Arrays.equals(((LegacyBeaconBeamSectionAccessor) section).biblioteca$color(), block.getColor().getTextureDiffuseColors());
        *///?}
    }

    private static BeaconBlockEntity.BeaconBeamSection biblioteca$average(BeaconBlockEntity.BeaconBeamSection section, BeaconBeamBlock block) {
        //? if >=1.21 {
        int a = ((LegacyBeaconBeamSectionAccessor) section).biblioteca$color();
        int b = block.getColor().getTextureDiffuseColor();
        int r = (((a >> 16) & 255) + ((b >> 16) & 255)) / 2;
        int g = (((a >> 8) & 255) + ((b >> 8) & 255)) / 2;
        int blue = ((a & 255) + (b & 255)) / 2;
        return new BeaconBlockEntity.BeaconBeamSection(0xff000000 | r << 16 | g << 8 | blue);
        //?} else {
        /*float[] a = ((LegacyBeaconBeamSectionAccessor) section).biblioteca$color();
        float[] b = block.getColor().getTextureDiffuseColors();
        return new BeaconBlockEntity.BeaconBeamSection(new float[]{
                (a[0] + b[0]) * 0.5f, (a[1] + b[1]) * 0.5f, (a[2] + b[2]) * 0.5f});
        *///?}
    }
}
