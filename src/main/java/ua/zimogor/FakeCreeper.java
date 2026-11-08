package ua.zimogor;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Заряженный крипер, который существует только на клиенте.
 * phantom = false: маленький крипер, крутится внутри поршня (украшение).
 * phantom = true: обычный крипер вокруг поршня: ходит за игроком, получает урон,
 * "взрывается" только визуально (урона игроку нет).
 */
public class FakeCreeper extends CreeperEntity {
    private final boolean phantom;
    private double vy;
    private double kbX;
    private double kbZ;
    private int fuse;
    private int deadTicks;
    private int hits;
    private static final int HITS_TO_KILL = 2; // умирает с двух ударов

    public FakeCreeper(World world, boolean phantom) {
        super(EntityType.CREEPER, world);
        this.phantom = phantom;
        this.setNoGravity(true);
        this.setAiDisabled(true);
        this.setSilent(!phantom);
        if (!phantom) {
            this.setInvulnerable(true);
            EntityAttributeInstance scale = this.getAttributeInstance(EntityAttributes.SCALE);
            if (scale != null) {
                scale.setBaseValue(0.3125); // как у моба в настоящем спавнере
            }
        }
    }

    public boolean isPhantom() {
        return phantom;
    }

    @Override
    public void tick() {
        super.tick();
        if (!phantom) {
            float yaw = (this.age * 10) % 360; // скорость вращения, как у спавнера
            this.setYaw(yaw);
            this.setBodyYaw(yaw);
            this.setHeadYaw(yaw);
            return;
        }

        if (this.isDead()) {
            if (++deadTicks >= 20) {
                this.discard();
            }
            return;
        }

        PlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return;
        }
        double dx = player.getX() - this.getX();
        double dz = player.getZ() - this.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        double mx = 0.0;
        double mz = 0.0;
        if (dist < 16.0 && dist > 1.5) {
            mx = dx / dist * 0.1;
            mz = dz / dist * 0.1;
        }
        mx += kbX;
        mz += kbZ;
        kbX *= 0.6;
        kbZ *= 0.6;

        if (dist > 0.01) {
            float yaw = (float) (MathHelper.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
            this.setYaw(yaw);
            this.setBodyYaw(yaw);
            this.setHeadYaw(yaw);
        }

        vy -= 0.08;
        vy *= 0.98;
        this.move(MovementType.SELF, new Vec3d(mx, vy, mz));
        if (this.isOnGround()) {
            vy = 0.0;
            if (this.horizontalCollision && dist > 1.5) {
                vy = 0.42; // перепрыгнуть блок
            }
        }

        boolean near = dist < 2.5 && Math.abs(player.getY() - this.getY()) < 2.0;
        this.setFuseSpeed(near ? 1 : -1);
        if (near) {
            if (++fuse >= 30) {
                MinecraftClient.getInstance().particleManager.addParticle(ParticleTypes.EXPLOSION_EMITTER,
                        this.getX(), this.getY() + 0.5, this.getZ(), 2.0, 0.0, 0.0);
                this.discard();
            }
        } else if (fuse > 0) {
            fuse--;
        }
    }

    /** Удар игрока: считаются удары (2 удара убивают), только у нас на клиенте. */
    public void clientHit(PlayerEntity player) {
        if (this.isDead()) {
            return;
        }
        player.resetLastAttackedTicks();
        hits++;
        if (hits >= HITS_TO_KILL) {
            this.setHealth(0.0f);
        }
        double dx = this.getX() - player.getX();
        double dz = this.getZ() - player.getZ();
        double d = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
        kbX = dx / d * 0.4;
        kbZ = dz / d * 0.4;
        vy = 0.3;
        this.handleStatus(this.isDead() ? (byte) 3 : (byte) 2);
    }

    /** Убить мгновенно (дамагер): только у нас на клиенте. */
    public void clientKill() {
        if (this.isDead()) {
            return;
        }
        this.setHealth(0.0f);
        this.handleStatus((byte) 3);
    }

    // Заряженный крипер (с электрическим свечением), как после удара молнии.
    @Override
    public boolean isCharged() {
        return true;
    }

    // Внутри сплошного блока света нет, поэтому "горим" без огня, чтобы крипер в поршне не был чёрным.
    @Override
    public boolean isOnFire() {
        return !phantom;
    }

    @Override
    public boolean doesRenderOnFire() {
        return false;
    }

    // Крипер в поршне недоступен курсору, а блуждающие можно бить.
    @Override
    public boolean canHit() {
        return phantom && !this.isDead();
    }
}
