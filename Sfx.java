package ua.zimogor;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.random.Random;

/**
 * Игра отбрасывает звуки клиентских мобов, поэтому проигрываем их сами.
 */
public final class Sfx {
    private Sfx() {
    }

    public static void play(SoundEvent sound, double x, double y, double z, float volume, float pitch) {
        MinecraftClient.getInstance().getSoundManager().play(
                new PositionedSoundInstance(sound, SoundCategory.HOSTILE, volume, pitch, Random.create(), x, y, z));
    }

    public static void play(RegistryEntry<SoundEvent> sound, double x, double y, double z, float volume, float pitch) {
        play(sound.value(), x, y, z, volume, pitch);
    }
}
