package ua.zimogor.mixin;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Делает арбуз "непрозрачным" только для рендера: чтобы соседние блоки и всё,
 * что за ним, рисовалось сквозь клетку, как у настоящего спавнера.
 */
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class AbstractBlockStateMixin {
    @Inject(method = "isOpaque", at = @At("HEAD"), cancellable = true)
    private void zimogor$melonNotOpaque(CallbackInfoReturnable<Boolean> cir) {
        if (((AbstractBlock.AbstractBlockState) (Object) this).isOf(Blocks.MELON)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isOpaqueFullCube", at = @At("HEAD"), cancellable = true)
    private void zimogor$melonNotFullCube(CallbackInfoReturnable<Boolean> cir) {
        if (((AbstractBlock.AbstractBlockState) (Object) this).isOf(Blocks.MELON)) {
            cir.setReturnValue(false);
        }
    }
}
