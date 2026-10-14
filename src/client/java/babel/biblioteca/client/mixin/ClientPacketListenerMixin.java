package babel.biblioteca.client.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Mirror-section dirty-marking for block updates is handled entirely by
 * ClientLevelDirtyMixin, which injects into ClientLevel.setBlocksDirty and
 * covers every code path that changes a block (network packets, predictions,
 * commands, etc.).
 *
 * The previous approach here — calling level.getBlockState(mirrorPos) and
 * passing the result as oldState to sendBlockUpdated — was broken: because
 * LevelBlockAccessMixin folds out-of-range Y coordinates back into the valid
 * range, getBlockState(y + height) returned the SAME state that had just been
 * written at the real position, making oldState == newState and causing
 * sendBlockUpdated to skip the re-render entirely.
 *
 * Keeping this class empty prevents that bug from re-appearing here while
 * still satisfying the fabric.mod.json mixin entry.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    // Intentionally empty — see class javadoc.
}
