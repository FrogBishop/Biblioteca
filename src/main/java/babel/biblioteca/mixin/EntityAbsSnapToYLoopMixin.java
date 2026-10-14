package babel.biblioteca.mixin;

/**
 * Intentionally empty.
 *
 * The setPos(DDD) injection approach was abandoned: folding the coordinate
 * there never notified the client of the new position, causing the server to
 * silently fold on every tick while the client kept sending the out-of-bounds
 * position forever.
 *
 * Player Y-folding (with client notification) is now handled entirely by
 * EntityYLoopMixin, which calls connection.teleport() after folding a
 * ServerPlayer's position.  Other entity types are covered by their own
 * direct tick() injections (PrimedTntYLoopMixin, FallingBlockEntityYLoopMixin,
 * AbstractMinecartYLoopMixin) and by EntityYLoopMixin for the base Entity path.
 *
 * Kept as an empty placeholder so the mixin-config entry compiles without
 * error until a future cleanup pass removes it.
 */
public class EntityAbsSnapToYLoopMixin {
    // Intentionally empty — see class javadoc.
}

