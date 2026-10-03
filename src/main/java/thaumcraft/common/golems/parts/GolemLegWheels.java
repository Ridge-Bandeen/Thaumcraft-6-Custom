package thaumcraft.common.golems.parts;

import java.util.HashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.golems.IGolemAPI;
import thaumcraft.api.golems.parts.GolemLeg;

/**
 * The original's roller legs: on the client the wheel's spin is tracked per golem (for the renderer) and, rolling
 * along the ground at speed, it kicks up dust of the block beneath.
 */
public class GolemLegWheels implements GolemLeg.ILegFunction {

	/** Entity id -> the wheel's current spin in degrees. */
	public static final HashMap<Integer, Float> ani = new HashMap<>();

	@Override
	public void onUpdateTick(IGolemAPI golem) {
		LivingEntity e = golem.getGolemEntity();
		if (!e.level().isClientSide) return;
		double d0 = e.getX() - e.xo;
		double d2 = e.getY() - e.yo;
		double d3 = e.getZ() - e.zo;
		double dist = Math.sqrt(d0 * d0 + d2 * d2 + d3 * d3);
		float lastRot = ani.getOrDefault(e.getId(), 0.0f);
		float dirTravel = (float) (Math.atan2(d3, d0) * 180.0 / Math.PI) - 90.0f;
		double dir = 360.0f - (e.getYRot() - dirTravel);
		lastRot += (float) (dist / 1.571 * dir);
		if (lastRot > 360.0f) lastRot -= 360.0f;
		ani.put(e.getId(), lastRot);
		if (e.onGround() && !e.isInWater() && dist > 0.25) {
			BlockPos pos = BlockPos.containing(e.getX(), e.getY() - 0.2, e.getZ());
			BlockState state = e.level().getBlockState(pos);
			if (state.getRenderShape() != RenderShape.INVISIBLE) {
				var rand = e.level().random;
				e.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state).setPos(pos),
						e.getX() + (rand.nextFloat() - 0.5) * e.getBbWidth(), e.getBoundingBox().minY + 0.1,
						e.getZ() + (rand.nextFloat() - 0.5) * e.getBbWidth(),
						-e.getDeltaMovement().x * 4.0, 1.5, -e.getDeltaMovement().z * 4.0);
			}
		}
	}
}
