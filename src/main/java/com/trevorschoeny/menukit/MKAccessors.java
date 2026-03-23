package com.trevorschoeny.menukit;

import net.minecraft.world.inventory.ContainerLevelAccess;

/**
 * Runtime-accessible interfaces for mixin accessors. These live in the
 * menukit package (not the mixin package) so they can be referenced
 * at runtime without Mixin's "cannot be referenced directly" restriction.
 *
 * <p>The actual {@code @Accessor} implementations live in the mixin package
 * and extend these interfaces.
 *
 * <p>Part of the <b>MenuKit</b> framework internals.
 */
public class MKAccessors {

    /**
     * Interface for menus that have a ContainerLevelAccess field.
     * Cast a menu to this to extract its block position.
     */
    public interface HasContainerLevelAccess {
        ContainerLevelAccess menuKit$getAccess();
    }
}
