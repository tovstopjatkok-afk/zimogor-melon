package ua.zimogor;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.GuardianEntity;
import net.minecraft.world.World;

/**
 * Маленький страж, который существует только на клиенте
 * и крутится внутри арбуза.
 */
public class FakeGuardian extends GuardianEntity {
    public FakeGuardian(World world) {
        super(EntityType.GUARDIAN, world);
        this.setNoGravity(true);
        this.setSilent(true);
        this.setInvulnerable(true);
        this.setAiDisabled(true);
        EntityAttributeInstance scale = this.getAttributeInstance(EntityAttributes.SCALE);
        if (scale != null) {
            scale.setBaseValue(0.5);
        }
    }

    @Override
    public void tick() {
        super.tick();
        float yaw = (this.age * 6) % 360;
        this.setYaw(yaw);
        this.setBodyYaw(yaw);
        this.setHeadYaw(yaw);
    }

    // Внутри сплошного блока света нет, поэтому "горим" без огня, чтобы страж не был чёрным.
    @Override
    public boolean isOnFire() {
        return true;
    }

    @Override
    public boolean doesRenderOnFire() {
        return false;
    }

    // Чтобы курсор целился в арбуз, а не в стража.
    @Override
    public boolean canHit() {
        return false;
    }
}
