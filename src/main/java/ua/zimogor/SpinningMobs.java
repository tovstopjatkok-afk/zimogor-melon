package ua.zimogor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PistonBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/**
 * Раз в полсекунды ищет поршни рядом с игроком и ставит в каждый
 * по крипперу. Если поршень пропал или далеко, они убираются.
 */
public final class SpinningMobs {
    private static final int RADIUS = 12;
    private static final int MAX_BLOCKS = 100;
    private static final int SCAN_EVERY_TICKS = 10;
    private static final int MAX_NEARBY = 6;

    private static final Map<BlockPos, List<Entity>> MOBS = new HashMap<>();
    private static final List<FakeCreeper> PHANTOMS = new ArrayList<>();
    private static final Map<BlockPos, Integer> SPAWN_DELAY = new HashMap<>();
    private static final Set<BlockPos> DAMAGERS = new HashSet<>();
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
        killByDamagers();
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

            FakeCreeper creeper = new FakeCreeper(world, false);
            creeper.setId(nextId++);
            creeper.setPosition(pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5);
            world.addEntity(creeper);

            MOBS.put(pos, List.of(creeper));
        }

        tickPhantoms(client, world, center);
    }

    /**
     * Как у настоящего спавнера: таймер 200-800 тиков (идёт, только если игрок ближе 16 блоков),
     * за цикл до 4 попыток появления в области +-4 по горизонтали и +-1 по вертикали,
     * не больше 6 криперов в области.
     */
    private static void tickPhantoms(MinecraftClient client, ClientWorld world, BlockPos center) {
        ThreadLocalRandom r = ThreadLocalRandom.current();

        PHANTOMS.removeIf(c -> {
            boolean drop = c.isRemoved() || c.squaredDistanceTo(client.player) > 48.0 * 48.0;
            if (drop) {
                c.discard();
            }
            return drop;
        });
        SPAWN_DELAY.keySet().retainAll(MOBS.keySet());

        for (BlockPos pos : MOBS.keySet()) {
            if (pos.getSquaredDistance(center) > 256) {
                continue;
            }
            int delay = SPAWN_DELAY.getOrDefault(pos, 20) - SCAN_EVERY_TICKS;
            if (delay > 0) {
                SPAWN_DELAY.put(pos, delay);
                continue;
            }
            SPAWN_DELAY.put(pos, 200 + r.nextInt(600));

            Box area = new Box(pos).expand(4.0);
            long nearby = PHANTOMS.stream().filter(c -> c.getBoundingBox().intersects(area)).count();
            if (nearby >= MAX_NEARBY) {
                continue;
            }

            boolean spawned = false;
            for (int i = 0; i < 4; i++) {
                double x = pos.getX() + (r.nextDouble() - r.nextDouble()) * 4.0 + 0.5;
                int by = pos.getY() + r.nextInt(3) - 1;
                double z = pos.getZ() + (r.nextDouble() - r.nextDouble()) * 4.0 + 0.5;
                BlockPos bp = BlockPos.ofFloored(x, by, z);
                if (world.getBlockState(bp).isAir() && world.getBlockState(bp.up()).isAir()) {
                    FakeCreeper creeper = new FakeCreeper(world, true);
                    creeper.setId(nextId++);
                    creeper.setPosition(x, by, z);
                    if (!world.isSpaceEmpty(creeper)) {
                        continue; // тесно: как у настоящего спавнера, в блоках не появляемся
                    }
                    world.addEntity(creeper);
                    creeper.handleStatus((byte) 20); // облачко появления, как у спавнера
                    PHANTOMS.add(creeper);
                    spawned = true;
                }
            }
            if (spawned) {
                for (int i = 0; i < 20; i++) {
                    double px = pos.getX() + 0.5 + (r.nextDouble() - 0.5) * 2.0;
                    double py = pos.getY() + 0.5 + (r.nextDouble() - 0.5) * 2.0;
                    double pz = pos.getZ() + 0.5 + (r.nextDouble() - 0.5) * 2.0;
                    client.particleManager.addParticle(ParticleTypes.SMOKE, px, py, pz, 0.0, 0.0, 0.0);
                    client.particleManager.addParticle(ParticleTypes.FLAME, px, py, pz, 0.0, 0.0, 0.0);
                }
            }
        }
    }

    /** Отметить или снять отметку с блока-дамагера. Возвращает true, если добавили. */
    public static boolean toggleDamager(BlockPos pos) {
        BlockPos p = pos.toImmutable();
        if (DAMAGERS.remove(p)) {
            return false;
        }
        DAMAGERS.add(p);
        return true;
    }

    /** Дамагер бьёт как игрок с мечом: каждого фантомного крипера в радиусе 2 блоков, 2 удара убивают. */
    private static void killByDamagers() {
        for (BlockPos pos : DAMAGERS) {
            double cx = pos.getX() + 0.5;
            double cy = pos.getY() + 0.5;
            double cz = pos.getZ() + 0.5;
            for (FakeCreeper c : PHANTOMS) {
                if (!c.isDead() && c.squaredDistanceTo(cx, cy, cz) <= 4.0) {
                    c.damagerHit(cx, cz);
                }
            }
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
        PHANTOMS.forEach(Entity::discard);
        PHANTOMS.clear();
        SPAWN_DELAY.clear();
        DAMAGERS.clear();
    }
}
