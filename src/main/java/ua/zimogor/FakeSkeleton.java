package ua.zimogor;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Скелет, который существует только на клиенте.
 * phantom = false: маленький скелет, крутится внутри поршня (украшение).
 * phantom = true: обычный скелет с луком вокруг поршня. Выбирает цель как настоящий
 * (любой игрок в радиусе 16 блоков с учётом невидимости, брони и прямой видимости),
 * держит дистанцию, кружит и стреляет стрелами (стрелы только визуальные),
 * подчиняется физике (вода, лёд, лава, падение).
 */
public class FakeSkeleton extends SkeletonEntity implements FakeMob {
    private static final int HITS_TO_KILL = 2; // умирает с двух ударов

    private final boolean phantom;
    private PlayerEntity target;
    private int targetTimer;
    private int strafeTimer;
    private boolean strafeClockwise;
    private boolean strafeBack;
    private int drawTicks;
    private int shootCooldown;
    private int stepTicks;
    private int stuckTicks;
    private int unstuckTicks;
    private double unstuckSign = 1.0;
    private int deadTicks;
    private double hits;
    private int lastHit = -100;
    private int lastDamagerHit = -100;
    private double peakY = Double.NaN;
    private int freezeTicks;
    private int snowTicks;
    private static final int CONVERT_TICKS = 160; // 8 сек
    private static final int FREEZE_TICKS = 120; // в ванили 140 тиков (7 сек), у нас на секунду быстрее

    public FakeSkeleton(World world, boolean phantom) {
        super(EntityType.SKELETON, world);
        this.phantom = phantom;
        this.setNoGravity(true);
        this.setAiDisabled(true);
        this.setSilent(!phantom);
        if (phantom) {
            this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            equipRandomArmor();
        } else {
            this.setInvulnerable(true);
            EntityAttributeInstance scale = this.getAttributeInstance(EntityAttributes.SCALE);
            if (scale != null) {
                scale.setBaseValue(0.267); // как у моба в настоящем спавнере (0.53125 / высота 1.99)
            }
        }
    }

    private static final String[] ARMOR_MATERIALS = {"leather", "golden", "iron", "copper"};
    private static final String[] ARMOR_PARTS = {"helmet", "chestplate", "leggings", "boots"};
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final double ARMOR_CHANCE = 0.3; // шанс на каждую часть брони

    /** С шансом надевает на каждую часть тела случайную броню (кожа, золото, железо, медь). */
    private void equipRandomArmor() {
        for (int i = 0; i < ARMOR_SLOTS.length; i++) {
            if (this.random.nextDouble() >= ARMOR_CHANCE) {
                continue;
            }
            // Медная броня есть не во всех версиях игры: если предмета нет, берём другой материал.
            for (int attempt = 0; attempt < 8; attempt++) {
                String material = ARMOR_MATERIALS[this.random.nextInt(ARMOR_MATERIALS.length)];
                Identifier id = Identifier.of("minecraft", material + "_" + ARMOR_PARTS[i]);
                if (Registries.ITEM.containsId(id)) {
                    this.equipStack(ARMOR_SLOTS[i], new ItemStack(Registries.ITEM.get(id)));
                    break;
                }
            }
        }
    }

    /** Полностью замёрзшим считается через 120 тиков (6 сек) вместо 140. */
    @Override
    public int getMinFreezeDamageTicks() {
        return FREEZE_TICKS;
    }

    /** Превращает скелета в странника (зимогора), сохраняя положение, скорость, экипировку и полученные удары. */
    private void convertToStray(ClientWorld world) {
        FakeStray stray = new FakeStray(world);
        stray.setId(SpinningMobs.nextEntityId());
        stray.setPosition(this.getX(), this.getY(), this.getZ());
        stray.setVelocity(this.getVelocity());
        stray.setYaw(this.getYaw());
        stray.setBodyYaw(this.getBodyYaw());
        stray.setHeadYaw(this.getHeadYaw());
        stray.hits = this.hits;
        stray.peakY = this.peakY;
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
                EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            stray.equipStack(slot, this.getEquippedStack(slot).copy());
        }
        world.addEntity(stray);
        stray.handleStatus((byte) 20);
        SpinningMobs.replacePhantom(this, stray);
        this.discard();
    }

    @Override
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

        // Цель: ближайший подходящий игрок (как у настоящего скелета).
        if (--targetTimer <= 0) {
            targetTimer = 5;
            target = findTarget(world);
        }
        PlayerEntity t = (target != null && target.isAlive()) ? target : null;

        double dx = 0.0;
        double dz = 0.0;
        double dist = Double.MAX_VALUE;
        double dist3d = Double.MAX_VALUE;
        boolean los = false;
        if (t != null) {
            dx = t.getX() - this.getX();
            dz = t.getZ() - this.getZ();
            dist = Math.sqrt(dx * dx + dz * dz);
            dist3d = this.distanceTo(t);
            los = this.canSee(t);
        }

        // Куда хочет идти (как BowAttackGoal): далеко или не видит - идёт к цели,
        // иначе кружит боком и держит дистанцию 4-11 блоков.
        double wx = 0.0;
        double wz = 0.0;
        if (t != null && dist > 0.01) {
            double fx = dx / dist;
            double fz = dz / dist;
            if (dist3d > 15.0 || !los) {
                wx = fx;
                wz = fz;
            } else {
                if (++strafeTimer >= 20) {
                    strafeTimer = 0;
                    if (Math.random() < 0.3) {
                        strafeClockwise = !strafeClockwise;
                    }
                    if (Math.random() < 0.3) {
                        strafeBack = !strafeBack;
                    }
                }
                if (dist3d > 11.25) {
                    strafeBack = false;
                } else if (dist3d < 3.75) {
                    strafeBack = true;
                }
                double forward = strafeBack ? -0.5 : 0.5;
                double side = strafeClockwise ? 0.5 : -0.5;
                wx = fx * forward - fz * side;
                wz = fz * forward + fx * side;
            }
            float yaw = (float) (MathHelper.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
            this.setYaw(yaw);
            this.setBodyYaw(yaw);
            this.setHeadYaw(yaw);
        }
        boolean wantMove = wx != 0.0 || wz != 0.0;

        // Стрельба: натягивает лук 20 тиков, выстрел, пауза 20 тиков.
        if (t != null && los && dist3d <= 15.0) {
            if (shootCooldown > 0) {
                shootCooldown--;
                this.setAttacking(false);
            } else {
                this.setAttacking(true);
                if (++drawTicks >= 20) {
                    drawTicks = 0;
                    shootCooldown = 20;
                    shoot(world, t);
                    this.setAttacking(false);
                }
            }
        } else {
            drawTicks = 0;
            this.setAttacking(false);
        }

        // Если упёрся в стену (и не перепрыгнуть), на время обходит её сбоку.
        if (wantMove && this.horizontalCollision) {
            if (++stuckTicks > 8) {
                stuckTicks = 0;
                unstuckTicks = 20;
                unstuckSign = Math.random() < 0.5 ? 1.0 : -1.0;
            }
        } else if (stuckTicks > 0) {
            stuckTicks--;
        }
        if (unstuckTicks > 0) {
            unstuckTicks--;
            double sx = -wz * unstuckSign;
            double sz = wx * unstuckSign;
            wx = sx;
            wz = sz;
        }

        // Если оказался внутри блоков, выталкиваем вверх.
        if (!world.isSpaceEmpty(this)) {
            for (double up = 0.1; up <= 1.5; up += 0.1) {
                if (world.isSpaceEmpty(this.getBoundingBox().offset(0.0, up, 0.0))) {
                    this.setPosition(this.getX(), this.getY() + up, this.getZ());
                    break;
                }
            }
        }

        // Рыхлый снег: проваливается (как у ванильных мобов без кожаных ботинок) и постепенно замерзает.
        BlockPos feet = BlockPos.ofFloored(this.getX(), this.getY(), this.getZ());
        boolean inSnow = world.getBlockState(feet).isOf(Blocks.POWDER_SNOW)
                || world.getBlockState(BlockPos.ofFloored(this.getX(), this.getEyeY(), this.getZ()))
                        .isOf(Blocks.POWDER_SNOW);
        {
            if (inSnow) {
                freezeTicks = Math.min(FREEZE_TICKS, freezeTicks + 1);
                // В ванили скелет превращается в странника за 15 сек (300 тиков), у нас 8 сек (160 тиков).
                if (++snowTicks >= CONVERT_TICKS) {
                    convertToStray(world);
                    return;
                }
            } else {
                freezeTicks = Math.max(0, freezeTicks - 2);
                snowTicks = 0;
            }
            this.setFrozenTicks(freezeTicks);
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

        // Замедление в паутине и рыхлом снегу (как у настоящих мобов: движение умножается, скорость гасится).
        double mx = 1.0;
        double my = 1.0;
        double mz = 1.0;
        boolean slowed = false;
        if (touchesBlock(world, Blocks.COBWEB)) {
            mx = 0.25;
            my = 0.0125; // падение в паутине в 4 раза медленнее (было 0.05)
            mz = 0.25;
            slowed = true;
        } else if (inSnow) {
            mx = 0.9;
            my = 1.5;
            mz = 0.9;
            slowed = true;
        }

        double beforeX = this.getX();
        double beforeZ = this.getZ();
        this.setVelocity(vx, vy, vz);
        this.move(MovementType.SELF, new Vec3d(vx * mx, vy * my, vz * mz));
        if (slowed) {
            this.setVelocity(0.0, 0.0, 0.0);
        }

        // Анимация ходьбы (ноги и руки), как у настоящих мобов.
        double moved = Math.sqrt((this.getX() - beforeX) * (this.getX() - beforeX)
                + (this.getZ() - beforeZ) * (this.getZ() - beforeZ));
        animateLimbs((float) Math.min(moved * 4.0, 1.0));

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
            Sfx.play(SoundEvents.ENTITY_SKELETON_STEP, this.getX(), this.getY(), this.getZ(), 0.15f, 1.0f);
        }
    }

    /** Касается ли скелет блока данного типа (паутина и т.п.). */
    private boolean touchesBlock(ClientWorld world, net.minecraft.block.Block block) {
        net.minecraft.util.math.Box box = this.getBoundingBox().contract(1.0e-7);
        for (BlockPos p : BlockPos.iterate(BlockPos.ofFloored(box.minX, box.minY, box.minZ),
                BlockPos.ofFloored(box.maxX, box.maxY, box.maxZ))) {
            if (world.getBlockState(p).isOf(block)) {
                return true;
            }
        }
        return false;
    }

    private static java.lang.reflect.Method limbUpdate;
    private static boolean limbInit;

    /** Двигает ноги и руки. Метод аниматора ищем по сигнатуре (void, только float-параметры), чтобы не зависеть от версии. */
    private void animateLimbs(float speed) {
        try {
            if (!limbInit) {
                limbInit = true;
                for (java.lang.reflect.Method m : this.limbAnimator.getClass().getDeclaredMethods()) {
                    if (m.getReturnType() != void.class || java.lang.reflect.Modifier.isStatic(m.getModifiers())
                            || m.getParameterCount() < 2) {
                        continue;
                    }
                    boolean allFloat = true;
                    for (Class<?> c : m.getParameterTypes()) {
                        allFloat &= c == float.class;
                    }
                    if (allFloat) {
                        m.setAccessible(true);
                        limbUpdate = m;
                        break;
                    }
                }
            }
            if (limbUpdate == null) {
                return;
            }
            Object[] args = limbUpdate.getParameterCount() == 2
                    ? new Object[] {speed, 0.4f}
                    : new Object[] {speed, 0.4f, 1.0f};
            limbUpdate.invoke(this.limbAnimator, args);
        } catch (Exception e) {
            limbUpdate = null;
        }
    }

    /** Визуальная стрела в сторону цели (урона нет, живёт 3 секунды). */
    private void shoot(ClientWorld world, PlayerEntity t) {
        ArrowEntity arrow = new ArrowEntity(EntityType.ARROW, world);
        arrow.setPosition(this.getX(), this.getEyeY() - 0.1, this.getZ());
        double dx = t.getX() - this.getX();
        double dy = t.getBodyY(0.3333333333333333) - arrow.getY();
        double dz = t.getZ() - this.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        arrow.setVelocity(dx, dy + horizontal * 0.2, dz, 1.6f, 10.0f);
        SpinningMobs.spawnArrow(world, arrow);
        Sfx.play(SoundEvents.ENTITY_SKELETON_SHOOT, this.getX(), this.getY(), this.getZ(), 1.0f, 1.0f);
    }

    /** Ближайший игрок (включая других), которого этот скелет "видит": дальность 16 блоков с учётом
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

    /**
     * Удар игрока, как у настоящего: сила зависит от перезарядки (быстрые клики по 20-100% удара),
     * после удара 10 тиков неуязвимость. Полный удар считается за 1, два полных убивают.
     */
    @Override
    public void clientHit(PlayerEntity player) {
        if (this.isDead()) {
            return;
        }
        float cooldown = player.getAttackCooldownProgress(0.5f);
        player.resetLastAttackedTicks();
        if (this.age - lastHit < 10) {
            return;
        }
        lastHit = this.age;
        applyHit(player.getX(), player.getZ(), 0.2 + cooldown * cooldown * 0.8, 1.0);
    }

    /** Удар дамагера: как игрок с мечом, полный удар не чаще раза в 13 тиков (перезарядка меча). */
    @Override
    public void damagerHit(double fromX, double fromZ) {
        if (this.isDead() || this.age - lastDamagerHit < 13 || this.age - lastHit < 10) {
            return;
        }
        lastDamagerHit = this.age;
        lastHit = this.age;
        applyHit(fromX, fromZ, 1.0, 0.5); // отдача дамагера в 2 раза меньше
    }

    private void applyHit(double fromX, double fromZ, double strength, double knockback) {
        hits += strength;
        if (hits >= HITS_TO_KILL - 1.0e-6) {
            this.setHealth(0.0f);
        }
        double dx = this.getX() - fromX;
        double dz = this.getZ() - fromZ;
        double d = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
        this.setVelocity(dx / d * 0.4 * knockback, 0.3 * knockback, dz / d * 0.4 * knockback);
        this.handleStatus(this.isDead() ? (byte) 3 : (byte) 2);
        Sfx.play(this.isDead() ? SoundEvents.ENTITY_SKELETON_DEATH : SoundEvents.ENTITY_SKELETON_HURT,
                this.getX(), this.getY(), this.getZ(), 1.0f, 1.0f);
    }

    /** Убить мгновенно (лава, падение): только у нас на клиенте. */
    @Override
    public void clientKill() {
        if (this.isDead()) {
            return;
        }
        this.setHealth(0.0f);
        this.handleStatus((byte) 3);
        Sfx.play(SoundEvents.ENTITY_SKELETON_DEATH, this.getX(), this.getY(), this.getZ(), 1.0f, 1.0f);
    }

    // Внутри сплошного блока света нет, поэтому "горим" без огня, чтобы скелет в поршне не был чёрным.
    @Override
    public boolean isOnFire() {
        return !phantom;
    }

    @Override
    public boolean doesRenderOnFire() {
        return false;
    }

    // Скелет в поршне недоступен курсору, а блуждающих можно бить.
    @Override
    public boolean canHit() {
        return phantom && !this.isDead();
    }
}
