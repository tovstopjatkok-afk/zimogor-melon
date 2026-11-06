package ua.zimogor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Раз в полсекунды ищет арбузы рядом с игроком и ставит в каждый
 * по стражу. Если арбуз пропал или далеко, страж убирается.
 */
public final class SpinningGuardians {
    private static final int RADIUS = 12;
    private static final int MAX_GUARDIANS = 100;
    private static final int SCAN_EVERY_TICKS = 10;

    private static final Map<BlockPos, FakeGuardian> GUARDIANS = new HashMap<>();
    private static ClientWorld lastWorld;
    private static int nextId = 0x7FFF0000;
    private static int timer;

    private SpinningGuardians() {
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
        if (++timer < SCAN_EVERY_TICKS) {
            return;
        }
        timer = 0;

        BlockPos center = client.player.getBlockPos();

        Iterator<Map.Entry<BlockPos, FakeGuardian>> it = GUARDIANS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, FakeGuardian> entry = it.next();
            BlockPos pos = entry.getKey();
            FakeGuardian guardian = entry.getValue();
            boolean gone = guardian.isRemoved()
                    || !world.getBlockState(pos).isOf(Blocks.MELON)
                    || pos.getManhattanDistance(center) > RADIUS * 3;
            if (gone) {
                guardian.discard();
                it.remove();
            }
        }

        for (BlockPos p : BlockPos.iterate(center.add(-RADIUS, -RADIUS, -RADIUS),
                center.add(RADIUS, RADIUS, RADIUS))) {
            if (GUARDIANS.size() >= MAX_GUARDIANS) {
                break;
            }
            if (!world.getBlockState(p).isOf(Blocks.MELON) || GUARDIANS.containsKey(p)) {
                continue;
            }
            BlockPos pos = p.toImmutable();
            FakeGuardian guardian = new FakeGuardian(world);
            guardian.setId(nextId++);
            guardian.setPosition(pos.getX() + 0.5, pos.getY() + 0.28, pos.getZ() + 0.5);
            world.addEntity(guardian);
            GUARDIANS.put(pos, guardian);
        }
    }

    private static void clear() {
        for (FakeGuardian guardian : GUARDIANS.values()) {
            guardian.discard();
        }
        GUARDIANS.clear();
    }
}
