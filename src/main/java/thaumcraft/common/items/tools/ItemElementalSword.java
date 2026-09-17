package thaumcraft.common.items.tools;

import java.util.List;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Replaces the old (1.12.2) {@code ItemElementalSword} (the Air-aspect elemental tool - real aspect cost
 * includes AVERSION, matching this real ability). Real ability, ported as-is: hold right-click to hover
 * (real upward thrust, damping any existing downward fall) while gently pushing every nearby real
 * {@link LivingEntity} (other than the wielder) away from you - a real "air gust" repulsion effect, the
 * opposite direction from {@link ItemElementalAxe}'s own item-pulling "magnet" (confirmed against the
 * original's real motion-vector math: the axe subtracts the away-pointing unit vector to pull inward, this
 * adds it to push outward). The original's anti-cheat "reset float kick counter" call is dropped - that was
 * a 1.12.2-era vanilla-server workaround with no real modern equivalent needed (a modern server doesn't kick
 * a player for server-applied vertical motion the way the old float-kick heuristic could).
 *
 * <p>Real client-only hover trail restored 2026-09-17 (was dropped under this port's old blanket "no client
 * FX" rule) - a real faithful port of the true original's own real per-tick smoke-spiral burst (5 real
 * spirals around the wielder's own feet each tick) plus a real vanilla smoke puff while grounded, routed
 * through {@link thaumcraft.client.fx.ClientFxHandlers#spawnSmokeSpiral} for the same real dist-crash-
 * avoidance reason every other client-only trigger in this port uses.
 */
public class ItemElementalSword extends SwordItem {

	public ItemElementalSword(Tier tier, Properties properties) {
		super(tier, properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		player.startUsingItem(hand);
		return InteractionResultHolder.success(player.getItemInHand(hand));
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.NONE;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return 72000;
	}

	@Override
	public void onUseTick(Level level, LivingEntity player, ItemStack stack, int remainingUseDuration) {
		Vec3 motion = player.getDeltaMovement();
		double y = motion.y;
		if (y < 0.0) y /= 1.2;
		y += 0.08;
		if (y > 0.5) y = 0.2;
		player.setDeltaMovement(motion.x, y, motion.z);
		player.fallDistance /= 1.2f;

		List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(2.5), e -> e != player);
		for (LivingEntity target : nearby) {
			if (target.isRemoved() || player.getVehicle() == target) continue;
			Vec3 toTarget = target.position().subtract(player.position());
			double distance = toTarget.length() + 0.1;
			target.setDeltaMovement(target.getDeltaMovement().add(toTarget.scale(1.0 / 2.5 / distance)));
		}

		if (level.isClientSide) {
			double minY = player.onGround() ? Math.floor(player.getBoundingBox().minY) : player.getBoundingBox().minY - 2.0;
			for (int a = 0; a < 5; a++) {
				thaumcraft.client.fx.ClientFxHandlers.spawnSmokeSpiral(level, player.getX(), player.getBoundingBox().minY + player.getBbHeight() / 2.0f, player.getZ(),
						1.5f, level.random.nextInt(360), (int) minY, 14540253);
			}
			if (player.onGround()) {
				float r2 = level.random.nextFloat() * 360.0f;
				float mx = -Mth.sin(r2 / 180.0f * Mth.PI) / 5.0f;
				float mz = Mth.cos(r2 / 180.0f * Mth.PI) / 5.0f;
				level.addParticle(ParticleTypes.SMOKE, player.getX(), player.getBoundingBox().minY + 0.1, player.getZ(), mx, 0.0, mz);
			}
		}
	}
}
