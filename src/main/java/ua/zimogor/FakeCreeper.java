package ua.zimogor;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Заряженный крипер, который существует только на клиенте.
 * phantom = false: маленький крипер, крутится внутри поршня (украшение).
 * phantom = true: обычный крипер вокруг поршня. Выбирает цель как настоящий крипер
 * (любой игрок в радиусе 16 блоков с учётом невидимости, брони и прямой видимости),
 * подчиняется физике (вода, лёд, лава, падение) и "взрывается" только визуально.
 */
public class FakeCreeper extends CreeperEntity {
    private static final int HITS_TO_KILL = 2; // умирает с двух ударов

    private final boolean phantom;
    private PlayerEntity target;
    private int targetTimer;
    private boolean igniting;
    private int fuse;
    private int stepTicks;
    private int deadTicks;
    private int hits;
    private double peakY = Double.NaN;

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

        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) {
            return;
        }
        if (this.getY() < world.getBottomY() - 16 || this.isInLava()) {
            this.clientKill();
            return;
        }

        // Цель: ближайший подходящий игрок (как у настоящего крипера).
        if (--targetTimer <= 0) {
            targetTimer = 5;
            target = findTarget(world);
        }
        PlayerEntity t = (target != null && target.isAlive()) ? target : null;

        double dx = 0.0;
        double dz = 0.0;
        double dist = Double.MAX_VALUE;
        double dist3d = Double.MAX_VALUE;
        if (t != null) {
            dx = t.getX() - this.getX();
            dz = t.getZ() - this.getZ();
            dist = Math.sqrt(dx * dx + dz * dz);
            dist3d = this.distanceTo(t);
        }

        // Поджиг как у настоящего: старт ближе 3 блоков, отмена дальше 7 блоков или без цели.
        igniting = t != null && (igniting ? dist3d < 7.0 : dist3d < 3.0);
        this.setFuseSpeed(igniting ? 1 : -1);
        if (igniting) {
            if (fuse == 0) {
                Sfx.play(SoundEvents.ENTITY_CREEPER_PRIMED, this.getX(), this.getY(), this.getZ(), 1.0f, 0.5f);
            }
            if (++fuse >= 30) {
                Sfx.play(SoundEvents.ENTITY_GENERIC_EXPLODE, this.getX(), this.getY(), this.getZ(), 4.0f, 1.0f);
                MinecraftClient.getInstance().particleManager.addParticle(ParticleTypes.EXPLOSION_EMITTER,
                        this.getX(), this.getY() + 0.5, this.getZ(), 2.0, 0.0, 0.0);
                this.discard();
                return;
            }
        } else if (fuse > 0) {
            fuse--;
        }

        // Куда хочет идти: к цели, пока не поджигается.
        double wx = 0.0;
        double wz = 0.0;
        if (t != null && !igniting && dist > 1.0) {
            wx = dx / dist;
            wz = dz / dist;
        }
        boolean wantMove = wx != 0.0 || wz != 0.0;
        if (t != null && dist > 0.01) {
            float yaw = (float) (MathHelper.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
            this.setYaw(yaw);
            this.setBodyYaw(yaw);
            this.setHeadYaw(yaw);
        }

        // Падение: смертельно с высоты больше 23 блоков.
        boolean water = this.isTouchingWater();
        boolean ground = this.isOnGround();
        if (Double.isNaN(peakY) || water) {
            peakY = this.getY();
        } else if (ground) {
            if (peakY - this.getY() > 23.0) {
                this.clientKill();
                return;
            }
            peakY = this.getY();
        } else if (this.getY() > peakY) {
            peakY = this.getY();
        }

        // Физика. Течение воды уже добавлено в скорость самой игрой (super.tick()).
        Vec3d v = this.getVelocity();
        double vx = v.x;
        double vy = v.y;
        double vz = v.z;

        BlockState belowState = world.getBlockState(BlockPos.ofFloored(this.getX(), this.getY() - 0.5000001, this.getZ()));
        double friction = ground ? belowState.getBlock().getSlipperiness() * 0.91 : 0.91;

        if (water) {
            vx += wx * 0.02;
            vz += wz * 0.02;
            if (this.getFluidHeight(FluidTags.WATER) > 0.4 || (this.horizontalCollision && wantMove)) {
                vy += 0.04; // всплывает
            }
        } else if (ground) {
            double accel = 0.0909 * Math.pow(0.546 / friction, 3);
            vx += wx * accel;
            vz += wz * accel;
            if (this.horizontalCollision && wantMove) {
                vy = 0.42; // перепрыгнуть блок
            }
        } else {
            vx += wx * 0.02;
            vz += wz * 0.02;
        }

        this.setVelocity(vx, vy, vz);
        this.move(MovementType.SELF, new Vec3d(vx, vy, vz));

        Vec3d after = this.getVelocity();
        vx = after.x;
        vy = after.y;
        vz = after.z;
        if (water) {
            vx *= 0.8;
            vz *= 0.8;
            vy = vy * 0.8 - 0.02;
        } else {
            vx *= friction;
            vz *= friction;
            vy = (vy - 0.08) * 0.98;
        }
        this.setVelocity(vx, vy, vz);

        // Шаги.
        if (this.isOnGround() && !water && wantMove && ++stepTicks >= 8) {
            stepTicks = 0;
            Sfx.play(belowState.getSoundGroup().getStepSound(), this.getX(), this.getY(), this.getZ(), 0.15f, 1.0f);
        }
    }

    /** Ближайший игрок (включая других), которого этот крипер "видит": дальность 16 блоков с учётом
     * невидимости и брони (как у настоящих мобов), не творческий и не наблюдатель, есть прямая видимость. */
    private PlayerEntity findTarget(ClientWorld world) {
        PlayerEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (PlayerEntity p : world.getPlayers()) {
            if (p.isSpectator() || p.isCreative() || !p.isAlive()) {
                continue;
            }
            double range = 16.0 * p.getAttackDistanceScalingFactor(this);
            double d = this.squaredDistanceTo(p);
            if (d > range * range || d >= bestDist || !this.canSee(p)) {
                continue;
            }
            best = p;
            bestDist = d;
        }
        return best;
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
        this.setVelocity(dx / d * 0.4, 0.3, dz / d * 0.4);
        this.handleStatus(this.isDead() ? (byte) 3 : (byte) 2);
        Sfx.play(this.isDead() ? SoundEvents.ENTITY_CREEPER_DEATH : SoundEvents.ENTITY_CREEPER_HURT,
                this.getX(), this.getY(), this.getZ(), 1.0f, 1.0f);
    }

    /** Убить мгновенно (дамагер, лава, падение): только у нас на клиенте. */
    public void clientKill() {
        if (this.isDead()) {
            return;
        }
        this.setHealth(0.0f);
        this.handleStatus((byte) 3);
        Sfx.play(SoundEvents.ENTITY_CREEPER_DEATH, this.getX(), this.getY(), this.getZ(), 1.0f, 1.0f);
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
