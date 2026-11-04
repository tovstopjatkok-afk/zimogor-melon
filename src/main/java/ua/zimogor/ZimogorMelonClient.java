package ua.zimogor;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.BlockRenderLayer;

/**
 * Арбуз использует текстуру спавнера (см. models/block/melon.json).
 * У текстуры спавнера прозрачные дырки в клетке, поэтому блок нужно
 * рисовать в слое CUTOUT, иначе вместо прозрачности будет чёрный фон.
 */
public class ZimogorMelonClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockRenderLayerMap.INSTANCE.putBlock(Blocks.MELON, BlockRenderLayer.CUTOUT);
    }
}
