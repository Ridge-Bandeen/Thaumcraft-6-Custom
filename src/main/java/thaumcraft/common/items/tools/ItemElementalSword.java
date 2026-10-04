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
 * Replaces the old (1.12.2) {@code ItemElementalSword}, the Sword of the Zephyr: as the original, holding right-click
 * lifts you (slowing a fall, and the fall distance with it) and pushes every non-player living thing within 2.5 blocks
 * away, wearing a point of durability each second with a gust of wind. It resets the server's flying-kick counter as
 * the original did, so a long hover is not taken for cheating.
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
		int ticks = getUseDuration(stack, player) - remainingUseDuration;
		if (y < 0.0) {
			y /= 1.2;
			player.fallDistance /= 1.2f;
		}
		y += 0.08;
		if (y > 0.5) y = 0.2;
		player.setDeltaMovement(motion.x, y, motion.z);
		if (player instanceof net.minecraft.server.level.ServerPlayer sp) sp.connection.aboveGroundTickCount = 0;

		List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(2.5), e -> e != player && !(e instanceof Player));
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
		} else if (ticks % 20 == 0) {
			player.playSound(thaumcraft.common.registry.ThaumcraftSounds.WIND.get(), 0.5f, 0.9f + level.random.nextFloat() * 0.2f);
		}
		if (ticks % 20 == 0) {
			stack.hurtAndBreak(1, player, player.getUsedItemHand() == InteractionHand.OFF_HAND ? net.minecraft.world.entity.EquipmentSlot.OFFHAND
					: net.minecraft.world.entity.EquipmentSlot.MAINHAND);
		}
	}
}
