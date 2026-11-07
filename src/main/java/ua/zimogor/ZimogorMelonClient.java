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
 * Арбуз использует текстуру спавнера (см. models/block/melon.json).
 * У текстуры спавнера прозрачные дырки в клетке, поэтому блок нужно
 * рисовать в слое CUTOUT, иначе вместо прозрачности будет чёрный фон.
 * Внутри каждого арбуза крутится страж (см. SpinningGuardians).
 */
public class ZimogorMelonClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockRenderLayerMap.putBlock(Blocks.MELON, BlockRenderLayer.CUTOUT);
        ClientTickEvents.END_CLIENT_TICK.register(SpinningGuardians::tick);

        // Отдельный встроенный ресурспак, всегда включён: перебивает ванильную модель арбуза.
        FabricLoader.getInstance().getModContainer("zimogor_melon").ifPresent(container ->
                ResourceManagerHelper.registerBuiltinResourcePack(
                        Identifier.of("zimogor_melon", "melon_spawner"),
                        container,
                        Text.literal("Cargo"),
                        ResourcePackActivationType.ALWAYS_ENABLED));
    }
}
