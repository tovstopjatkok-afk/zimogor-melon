package ua.zimogor;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

/**
 * Поршни используют текстуру спавнера (см. blockstates/piston.json).
 * У текстуры спавнера прозрачные дырки в клетке, поэтому блок нужно
 * рисовать в слое CUTOUT, иначе вместо прозрачности будет чёрный фон.
 * Внутри каждого поршня крутится скелет (см. SpinningMobs).
 */
public class ZimogorMelonClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockRenderLayerMap.putBlock(Blocks.PISTON, BlockRenderLayer.CUTOUT);
        BlockRenderLayerMap.putBlock(Blocks.STICKY_PISTON, BlockRenderLayer.CUTOUT);
        ClientTickEvents.END_CLIENT_TICK.register(SpinningMobs::tick);

        // Клавиша K: отметить блок под прицелом как дамагер (повторное нажатие снимает отметку).
        KeyBinding markDamager = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.cargo.damager", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "category.cargo"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (markDamager.wasPressed()) {
                if (client.player != null && client.crosshairTarget instanceof BlockHitResult hit
                        && hit.getType() == HitResult.Type.BLOCK) {
                    boolean added = SpinningMobs.toggleDamager(hit.getBlockPos());
                    client.player.sendMessage(Text.literal(added ? "Дамагер отмечен" : "Дамагер снят"), true);
                }
            }
        });

        // Отдельный встроенный ресурспак, всегда включён: перебивает ванильные модели поршней.
        FabricLoader.getInstance().getModContainer("zimogor_melon").ifPresent(container ->
                ResourceManagerHelper.registerBuiltinResourcePack(
                        Identifier.of("zimogor_melon", "melon_spawner"),
                        container,
                        Text.literal("Cargo"),
                        ResourcePackActivationType.ALWAYS_ENABLED));
    }
}
