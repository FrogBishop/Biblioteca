package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Fold snapshot sampling, keeping the array indices in the original section's coordinate frame. */
@Mixin(RenderChunkRegion.class)
public abstract class LegacyRegionSnapshotMixin {
    @Shadow @Final protected Level level;
    private BlockPos biblioteca$source(BlockPos pos) {
        return LoopingRegistry.isLooping(this.level) ? SeamPositions.foldBlockPos(this.level, pos) : pos;
    }
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/LevelChunk;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState biblioteca$sampleBlock(LevelChunk chunk, BlockPos pos, Operation<BlockState> original) {
        return original.call(chunk, biblioteca$source(pos));
    }
    //? if <1.17 {
    /*@WrapOperation(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/LevelChunk;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"))
    private FluidState biblioteca$sampleFluid(LevelChunk chunk, BlockPos pos, Operation<FluidState> original) {
        return original.call(chunk, biblioteca$source(pos));
    }
    *///?}
    @ModifyVariable(method = "getBlockEntity(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/chunk/LevelChunk$EntityCreationType;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos biblioteca$blockEntity(BlockPos pos) { return biblioteca$source(pos); }
}
