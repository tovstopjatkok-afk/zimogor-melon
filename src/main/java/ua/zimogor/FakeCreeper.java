package ua.zimogor;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.world.World;

/**
 * Маленький крипер, который существует только на клиенте
 * и крутится внутри арбуза.
 */
public class FakeCreeper extends CreeperEntity {
    public FakeCreeper(World world) {
        super(EntityType.CREEPER, world);
        this.setNoGravity(true);
        this.setSilent(true);
        this.setInvulnerable(true);
        this.setAiDisabled(true);
        EntityAttributeInstance scale = this.getAttributeInstance(EntityAttributes.SCALE);
        if (scale != null) {
            scale.setBaseValue(0.3125); // как у моба в настоящем спавнере (0.53125 / высота 1.7)
        }
    }

    @Override
    public void tick() {
        super.tick();
        float yaw = (this.age * 10) % 360; // скорость вращения, как у спавнера
        this.setYaw(yaw);
        this.setBodyYaw(yaw);
        this.setHeadYaw(yaw);
    }

    // Внутри сплошного блока света нет, поэтому "горим" без огня, чтобы крипер не был чёрным.
    @Override
    public boolean isOnFire() {
        return true;
    }

    @Override
    public boolean doesRenderOnFire() {
        return false;
    }

    // Чтобы курсор целился в арбуз, а не в крипера.
    @Override
    public boolean canHit() {
        return false;
    }
}
