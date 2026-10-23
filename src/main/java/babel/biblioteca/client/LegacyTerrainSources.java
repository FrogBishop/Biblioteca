package babel.biblioteca.client;

import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import java.util.List;

/** The native visible source list on releases predating SectionOcclusionGraph. */
public interface LegacyTerrainSources {
    List<SectionRenderDispatcher.RenderSection> biblioteca$terrainSources();
    net.minecraft.client.renderer.culling.Frustum biblioteca$terrainFrustum();
}
