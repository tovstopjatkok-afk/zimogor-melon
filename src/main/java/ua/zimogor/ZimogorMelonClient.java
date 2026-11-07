package ua.zimogor;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Поршни используют текстуру спавнера (см. blockstates/piston.json).
 * У текстуры спавнера прозрачные дырки в клетке, поэтому блок нужно
 * рисовать в слое CUTOUT, иначе вместо прозрачности будет чёрный фон.
 * Внутри каждого поршня крутится крипер (см. SpinningMobs).
 */
public class ZimogorMelonClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockRenderLayerMap.putBlock(Blocks.PISTON, BlockRenderLayer.CUTOUT);
        BlockRenderLayerMap.putBlock(Blocks.STICKY_PISTON, BlockRenderLayer.CUTOUT);
        ClientTickEvents.END_CLIENT_TICK.register(SpinningMobs::tick);

        // Отдельный встроенный ресурспак, всегда включён: перебивает ванильные модели поршней.
        FabricLoader.getInstance().getModContainer("zimogor_melon").ifPresent(container ->
                ResourceManagerHelper.registerBuiltinResourcePack(
                        Identifier.of("zimogor_melon", "melon_spawner"),
                        container,
                        Text.literal("Cargo"),
                        ResourcePackActivationType.ALWAYS_ENABLED));
    }
}
