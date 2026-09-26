package thaumcraft.common.items.tools;

import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.research.ScanningManager;

/**
 * Replaces the old (1.12.2) {@code ItemThaumometer}. Right-clicking any registered scan target (an entity
 * or a block - see {@link ScanningManager}) grants its linked research key, the first real consumer of that
 * newly-real system. Dispatches through the modern per-target-kind hooks ({@link #interactLivingEntity} for
 * entities, {@link #onItemUseFirst} for blocks, {@link #use} for neither) instead of the original's single
 * manual raytrace covering all three - three real entry points instead of one, but each individually
 * simpler.
 *
 * <p>{@link #use} (fires only when neither of the other two hooks already consumed the interaction, i.e.
 * genuinely nothing in reach was targeted) was originally dropped as low-value on its own - restored once
 * {@link thaumcraft.common.lib.research.ScanSky} needed it for real: that device's own real trigger IS
 * "scanned nothing, but looked at the sky" ({@code obj == null} is a real, load-bearing part of its {@code
 * checkThing} contract, not an edge case), so this hook needed to genuinely exist for
 * {@link thaumcraft.common.lib.research.theorycraft.CardCelestial}'s whole dependency chain to be reachable
 * at all.
 *
 * <p>Restored 2026-09-25 (playtest): while held (main hand, or off hand) the client looks for things it could still
 * teach the player about - the entity being pointed at, and a block along a ray jittered up to 25 degrees either way -
 * and lights them with blue sparkles ({@code scanHighlight}), so unscanned things glow; a right click that is not
 * on something adjacent now reaches out the way the original did (an entity up to 9 blocks, else a block in reach,
 * else the sky), and the scan sound plays.
 *
 * Simplified vs the original: no aura-chunk HUD overlay (the original's {@code onUpdate} pushed a client
 * aura-display packet every 20 ticks while held) - real client HUD work, same boundary as every other device
 * this session. The scan-target highlight particles ({@code drawFX}) ARE restored (2026-09-17, was dropped
 * under this port's old blanket "no client FX" rule) - routed through
 * {@link thaumcraft.client.fx.ClientFxHandlers#spawnBlockRunes} for the same real dist-crash-avoidance reason
 * every other client-only trigger in this port uses.
 */
public class ItemThaumometer extends Item {

	public ItemThaumometer(Properties properties) {
		super(properties.stacksTo(1));
	}

	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
		super.inventoryTick(stack, level, entity, slot, selected);
		if (!level.isClientSide || !(entity instanceof Player player) || entity.tickCount % 5 != 0) return;
		if (!selected && player.getOffhandItem() != stack) return;

		Entity target = pointedEntity(level, player, 16.0, 5.0f);
		if (target != null && ScanningManager.isThingStillScannable(player, target)) {
			thaumcraft.client.fx.ClientFxHandlers.scanHighlight(level, target.getBoundingBox());
		}
		// the original's "wild" ray: the view direction wobbled by up to 25 degrees, so nearby scannables shimmer too
		float pitch = player.getXRot() + level.random.nextInt(25) - level.random.nextInt(25);
		float yaw = player.getYRot() + level.random.nextInt(25) - level.random.nextInt(25);
		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(Vec3.directionFromRotation(pitch, yaw).scale(16.0)),
				ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
		if (hit.getType() == HitResult.Type.BLOCK && ScanningManager.isThingStillScannable(player, hit.getBlockPos())) {
			var pos = hit.getBlockPos();
			AABB box = level.getBlockState(pos).getShape(level, pos).isEmpty() ? new AABB(0, 0, 0, 1, 1, 1) : level.getBlockState(pos).getShape(level, pos).bounds();
			thaumcraft.client.fx.ClientFxHandlers.scanHighlight(level, box.move(pos));
		}
	}

	/** The nearest living-or-not entity along the player's view within {@code range}, unless a block is in the way. */
	@javax.annotation.Nullable
	static Entity pointedEntity(Level level, Player player, double range, float padding) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 end = eye.add(look.scale(range));
		AABB box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0 + padding);
		EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player, eye, end, box,
				e -> e != player && !e.isSpectator(), range * range);
		if (hit == null) return null;
		BlockHitResult block = level.clip(new ClipContext(eye, hit.getLocation(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return block.getType() == HitResult.Type.MISS ? hit.getEntity() : null;
	}

	@Override
	public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
		if (player.level().isClientSide) {
			Level level = player.level();
			for (int a = 0; a < 10; a++) {
				thaumcraft.client.fx.ClientFxHandlers.spawnBlockRunes(level, target.getX() - 0.5, target.getY() + target.getEyeHeight() / 2.0f, target.getZ() - 0.5,
						0.3f + level.random.nextFloat() * 0.7f, 0.0f, 0.3f + level.random.nextFloat() * 0.7f, (int) (target.getBbHeight() * 15.0f), 0.03f);
			}
			return InteractionResult.SUCCESS;
		}
		ScanningManager.scanTheThing(player, target);
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
		Player player = context.getPlayer();
		Level level = context.getLevel();
		if (player == null) return InteractionResult.PASS;
		if (level.isClientSide) {
			var pos = context.getClickedPos();
			for (int a = 0; a < 10; a++) {
				thaumcraft.client.fx.ClientFxHandlers.spawnBlockRunes(level, pos.getX(), pos.getY() + 0.25, pos.getZ(),
						0.3f + level.random.nextFloat() * 0.7f, 0.0f, 0.3f + level.random.nextFloat() * 0.7f, 15, 0.03f);
			}
			return InteractionResult.SUCCESS;
		}
		ScanningManager.scanTheThing(player, context.getClickedPos());
		return InteractionResult.SUCCESS;
	}

	/** A right click that was not on something adjacent: reach out as the original's {@code onItemRightClick} did - an entity up to
	 * 9 blocks away, else the block in reach, else nothing (which is what scans the sky). */
	@Override
	public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		Entity target = pointedEntity(level, player, 9.0, 0.0f);
		BlockHitResult block = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
		if (level.isClientSide) {
			level.playLocalSound(player.getX(), player.getY(), player.getZ(), thaumcraft.common.registry.ThaumcraftSounds.SCAN.get(), SoundSource.PLAYERS, 0.5f, 1.0f, false);
			if (target != null) {
				for (int a = 0; a < 10; a++) {
					thaumcraft.client.fx.ClientFxHandlers.spawnBlockRunes(level, target.getX() - 0.5, target.getY() + target.getEyeHeight() / 2.0f, target.getZ() - 0.5,
							0.3f + level.random.nextFloat() * 0.7f, 0.0f, 0.3f + level.random.nextFloat() * 0.7f, (int) (target.getBbHeight() * 15.0f), 0.03f);
				}
			} else if (block.getType() == HitResult.Type.BLOCK) {
				for (int a = 0; a < 10; a++) {
					thaumcraft.client.fx.ClientFxHandlers.spawnBlockRunes(level, block.getBlockPos().getX(), block.getBlockPos().getY() + 0.25, block.getBlockPos().getZ(),
							0.3f + level.random.nextFloat() * 0.7f, 0.0f, 0.3f + level.random.nextFloat() * 0.7f, 15, 0.03f);
				}
			}
			return net.minecraft.world.InteractionResultHolder.success(stack);
		}
		if (target != null) {
			ScanningManager.scanTheThing(player, target);
		} else if (block.getType() == HitResult.Type.BLOCK) {
			ScanningManager.scanTheThing(player, block.getBlockPos());
		} else {
			ScanningManager.scanTheThing(player, null);
		}
		return net.minecraft.world.InteractionResultHolder.success(stack);
	}
}
