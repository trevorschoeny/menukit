package com.trevlar.menukit;

import org.jetbrains.annotations.ApiStatus;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MenuKit's mixin config plugin: watches the frame centring's height mixin, which takes
 * {@code require = 0} so another mod that {@code @Overwrite}s a vanilla container screen method
 * can't stop the game from starting. Each class it targets is noted as the config prepares, and
 * whether anything was injected into it as it loads; {@link #checkCentring} loads every one at
 * client start and warns, by name, of any that came out with nothing, whose frame then doesn't
 * move with its tab rows.
 */
@ApiStatus.Internal
public final class MenuKitMixins implements IMixinConfigPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger("menukit");
    private static final String HEIGHT_MIXIN = "MKFrameCentringHeightMixin";
    /** The height mixin's handler, by the name it is merged under (whatever prefix Mixin gives it). */
    private static final String HANDLER = "mk$centredHeight";

    private static final Set<String> TARGETS = ConcurrentHashMap.newKeySet();
    private static final Set<String> INJECTED = ConcurrentHashMap.newKeySet();

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("." + HEIGHT_MIXIN)) TARGETS.add(targetClassName.replace('/', '.'));
        return true;
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (!mixinClassName.endsWith("." + HEIGHT_MIXIN)) return;
        for (MethodNode m : targetClass.methods) {
            if (m.name.contains(HANDLER) || m.instructions == null) continue;
            for (AbstractInsnNode insn : m.instructions) {
                if (insn instanceof MethodInsnNode call && call.name.contains(HANDLER)) {
                    INJECTED.add(targetClassName.replace('/', '.'));
                    return;
                }
            }
        }
    }

    /**
     * Loads every class the height mixin targets (without initializing it) and warns of each
     * one it injected nothing into. Called once at client start.
     */
    public static void checkCentring() {
        List<String> missed = new ArrayList<>();
        for (String name : TARGETS) {
            try {
                Class.forName(name, false, MenuKitMixins.class.getClassLoader());
            } catch (ClassNotFoundException | LinkageError e) {
                LOGGER.warn("[MenuKit] Couldn't load {} to check its frame centring", name, e);
                continue;
            }
            if (!INJECTED.contains(name)) missed.add(name);
        }
        if (missed.isEmpty()) {
            LOGGER.debug("[MenuKit] Frame centring is in all {} container screen classes it targets", TARGETS.size());
        } else {
            missed.sort(null);
            LOGGER.warn("[MenuKit] Frame centring found nothing to change in {}: another mod likely replaced the "
                    + "methods it reads the window in, so on these screens the frame won't move with its tab rows", missed);
        }
    }
}
