package com.trevorschoeny.menukit.examples.injection;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Mixin-config plugin that gates the Phase 10 injection examples on the
 * development environment.
 *
 * <p>The example mixins compile and ship with menukit's source for reference
 * and type-checking, but only APPLY when the dev client is running. Production
 * {@code menukit.jar} loads the example classes as dormant bytes — they do not
 * decorate any vanilla screens. This preserves the "no defaults" rule from the
 * inventory-injection design doc: MenuKit never ships UI that applies to
 * consumer installs without explicit opt-in.
 *
 * <p>Detection is {@link FabricLoader#isDevelopmentEnvironment()}: {@code true}
 * in the dev client (Fabric Loom's {@code runClient} and similar), {@code false}
 * in every other environment.
 */
public final class DevOnlyExampleMixinsPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass,
                         String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass,
                          String mixinClassName, IMixinInfo mixinInfo) {}
}
