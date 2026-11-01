package babel.biblioteca.client.mixin;

import babel.biblioteca.client.EntityCopyState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public abstract class EntityCopyStateMixin implements EntityCopyState {
    @Unique private double biblioteca$sourceY;
    public double biblioteca$sourceY() { return biblioteca$sourceY; }
    public void biblioteca$sourceY(double value) { biblioteca$sourceY = value; }
}
