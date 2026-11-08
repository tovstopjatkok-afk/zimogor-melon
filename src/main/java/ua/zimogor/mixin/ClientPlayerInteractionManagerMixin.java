package ua.zimogor.mixin;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.zimogor.FakeCreeper;

/**
 * Удар по клиентскому крипреру обрабатывается локально и не отправляется на сервер.
 */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {
    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    private void zimogor$attackFake(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (target instanceof FakeCreeper creeper && creeper.isPhantom()) {
            creeper.clientHit(player);
            ci.cancel();
        }
    }
}
