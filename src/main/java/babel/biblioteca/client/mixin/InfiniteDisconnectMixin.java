package babel.biblioteca.client.mixin;

import babel.biblioteca.client.LoopingDimensionCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class InfiniteDisconnectMixin {
    @Inject(method = "onDisconnect", at = @At("TAIL"))
    private void biblioteca$clearState(CallbackInfo ci) {
        Minecraft.getInstance().execute(LoopingDimensionCache::clear);
    }
}
