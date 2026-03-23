package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.MenuKit;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into ServerPlayer's save/load to persist MenuKit container data
 * in the player's NBT save file.
 *
 * <p>Each consuming mod's containers are saved/loaded automatically — no
 * per-mod mixin needed. This single hook handles all registered
 * {@code MKContainer}s.
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
@Mixin(ServerPlayer.class)
public class MKServerPlayerMixin {

    @Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
    private void menuKit$saveData(ValueOutput output, CallbackInfo ci) {
        MenuKit.saveAll(
                ((ServerPlayer)(Object)this).getUUID(),
                output.child("trevormod_menukit"));
    }

    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
    private void menuKit$loadData(ValueInput input, CallbackInfo ci) {
        input.child("trevormod_menukit").ifPresent(mkInput ->
                MenuKit.loadAll(
                        ((ServerPlayer)(Object)this).getUUID(),
                        mkInput));
    }
}
