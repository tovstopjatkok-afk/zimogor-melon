package ua.zimogor;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

/** Общее для клиентских скелетов и зимогоров (странников). */
public interface FakeMob {
    boolean isPhantom();

    void clientHit(PlayerEntity player);

    void damagerHit(double fromX, double fromZ);

    void clientKill();

    default LivingEntity self() {
        return (LivingEntity) this;
    }
}
