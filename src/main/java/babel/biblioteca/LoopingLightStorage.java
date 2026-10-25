package babel.biblioteca;

import net.minecraft.world.level.chunk.DataLayer;

/** The stable storage operations used by both generations of the sky-light solver. */
public interface LoopingLightStorage {
    boolean biblioteca$stores(long section);
    int biblioteca$get(long block);
    void biblioteca$set(long block, int value);
    DataLayer biblioteca$writable(long section);
    void biblioteca$sectionStatus(long section, boolean empty);
    void biblioteca$queue(long section, DataLayer data);
    void biblioteca$dirty(long section);
    default void biblioteca$allocateSections() {}
}
