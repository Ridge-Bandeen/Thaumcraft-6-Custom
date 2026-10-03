package thaumcraft.common.golems.parts;

import net.minecraft.world.entity.LivingEntity;
import thaumcraft.api.golems.IGolemAPI;
import thaumcraft.api.golems.parts.GolemLeg;

/** The original's flyer legs: on the client a faint trail of motes drifts down beneath the golem - steadily in the air,
 * every fifth tick on the ground. */
public class GolemLegLevitator implements GolemLeg.ILegFunction {

	@Override
	public void onUpdateTick(IGolemAPI golem) {
		LivingEntity e = golem.getGolemEntity();
		if (e.level().isClientSide && (!e.onGround() || e.tickCount % 5 == 0)) {
			var rand = e.level().random;
			thaumcraft.client.fx.ClientFxHandlers.spawnGolemFly(e.level(), e.getX(), e.getY() + 0.1, e.getZ(),
					rand.nextGaussian() / 100.0, -0.1, rand.nextGaussian() / 100.0);
		}
	}
}
