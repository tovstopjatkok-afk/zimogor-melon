package ua.zimogor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PistonBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;

/**
 * Раз в полсекунды ищет поршни рядом с игроком и ставит в каждый
 * по крипперу. Если поршень пропал или далеко, они убираются.
 */
public final class SpinningMobs {
    private static final int RADIUS = 12;
    private static final int MAX_BLOCKS = 100;
    private static final int SCAN_EVERY_TICKS = 10;

    private static final Map<BlockPos, List<Entity>> MOBS = new HashMap<>();
    private static ClientWorld lastWorld;
    private static int nextId = 0x7FFF0000;
    private static int timer;

    private SpinningMobs() {
    }

    public static void tick(MinecraftClient client) {
        ClientWorld world = client.world;
        if (world != lastWorld) {
            clear();
            lastWorld = world;
        }
        if (world == null || client.player == null) {
            return;
        }
        spawnParticles(client);
        if (++timer < SCAN_EVERY_TICKS) {
            return;
        }
        timer = 0;

        BlockPos center = client.player.getBlockPos();

        Iterator<Map.Entry<BlockPos, List<Entity>>> it = MOBS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, List<Entity>> entry = it.next();
            BlockPos pos = entry.getKey();
            boolean gone = !isTarget(world.getBlockState(pos))
                    || pos.getManhattanDistance(center) > RADIUS * 3
                    || entry.getValue().stream().anyMatch(Entity::isRemoved);
            if (gone) {
                entry.getValue().forEach(Entity::discard);
                it.remove();
            }
        }

        for (BlockPos p : BlockPos.iterate(center.add(-RADIUS, -RADIUS, -RADIUS),
                center.add(RADIUS, RADIUS, RADIUS))) {
            if (MOBS.size() >= MAX_BLOCKS) {
                break;
            }
            if (!isTarget(world.getBlockState(p)) || MOBS.containsKey(p)) {
                continue;
            }
            BlockPos pos = p.toImmutable();

            FakeCreeper creeper = new FakeCreeper(world);
            creeper.setId(nextId++);
            creeper.setPosition(pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5);
            world.addEntity(creeper);

            MOBS.put(pos, List.of(creeper));
        }
    }

    /** Поршень (обычный или липкий), не выдвинутый. */
    private static boolean isTarget(BlockState state) {
        return (state.isOf(Blocks.PISTON) || state.isOf(Blocks.STICKY_PISTON))
                && !state.get(PistonBlock.EXTENDED);
    }

    /** Дым и огоньки внутри поршней, как у настоящего спавнера. */
    private static void spawnParticles(MinecraftClient client) {
        BlockPos center = client.player.getBlockPos();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (BlockPos pos : MOBS.keySet()) {
            if (pos.getSquaredDistance(center) > 256 || r.nextInt(3) != 0) {
                continue;
            }
            client.particleManager.addParticle(ParticleTypes.SMOKE,
                    pos.getX() + r.nextDouble(), pos.getY() + r.nextDouble(), pos.getZ() + r.nextDouble(),
                    0.0, 0.0, 0.0);
            client.particleManager.addParticle(ParticleTypes.FLAME,
                    pos.getX() + r.nextDouble(), pos.getY() + r.nextDouble(), pos.getZ() + r.nextDouble(),
                    0.0, 0.0, 0.0);
        }
    }

    private static void clear() {
        for (List<Entity> list : MOBS.values()) {
            list.forEach(Entity::discard);
        }
        MOBS.clear();
    }
}
