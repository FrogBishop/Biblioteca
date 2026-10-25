package babel.biblioteca;

import net.minecraft.core.SectionPos;

/** Light-engine lifecycle bridge; rebuilds run on the normal lighting thread. */
public interface LoopingSkyLight {
    boolean biblioteca$hasSkyWork();
    void biblioteca$prepareSkyUpdates();
    boolean biblioteca$skySectionEmpty(SectionPos section, boolean actuallyEmpty);
}
