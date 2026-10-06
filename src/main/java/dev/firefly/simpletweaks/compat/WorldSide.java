package dev.firefly.simpletweaks.compat;

import net.minecraft.world.World;

/**
 * Unambiguous client-side test.
 *
 * <p>Yarn 1.21.1 declares {@code World.isClient} as a public <b>field</b> (field_9236), while the
 * {@code WorldView} super-interface it implements declares an {@code isClient()} <b>method</b>
 * (method_8608). Java keeps fields and methods in separate namespaces so {@code world.isClient}
 * is unambiguous here, but Kotlin's synthetic-property resolution for Java members can pick the
 * wrong one (or report an overload ambiguity) when both are visible.
 *
 * <p>Verified against the cached yarn mappings. Since the mixins in this mod are written in Java
 * and the ported handlers are Kotlin, routing the check through this helper removes the hazard
 * from every handler at once.
 */
public final class WorldSide {

    private WorldSide() {
    }

    public static boolean isClient(World world) {
        return world.isClient;
    }

    public static boolean isServer(World world) {
        return !world.isClient;
    }
}
