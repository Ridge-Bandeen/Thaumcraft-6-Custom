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
 * While held it sends the chunk's aura to the client every 20 ticks for the gauge, and starts the Flux research
 * in a flux-heavy chunk, as the original's {@code onUpdate} did. The scan-target highlight particles ({@code drawFX}) ARE restored (2026-09-17, was dropped
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
		if (!level.isClientSide && entity.tickCount % 20 == 0 && entity instanceof net.minecraft.server.level.ServerPlayer sp
				&& (selected || sp.getOffhandItem() == stack)) {
			updateAura(sp);
		}
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

	/** The original's {@code updateAura}: a flux-heavy chunk starts the Flux research (with its warning), and the chunk's
	 * aura goes to the client for the gauge ({@link thaumcraft.client.gui.HudOverlays}). */
	private static void updateAura(net.minecraft.server.level.ServerPlayer player) {
		var ac = thaumcraft.common.world.aura.AuraHandler.getAuraChunk(player.level(), player.blockPosition().getX() >> 4, player.blockPosition().getZ() >> 4);
		if (ac == null) return;
		if ((ac.getFlux() > ac.getVis() || ac.getFlux() > ac.getBase() / 3) && !thaumcraft.api.capabilities.ThaumcraftCapabilities.knowsResearch(player, "FLUX")) {
			if (thaumcraft.api.ThaumcraftApi.internalMethods.progressResearch(player, "FLUX")) {
				var knowledge = thaumcraft.api.capabilities.ThaumcraftCapabilities.getKnowledge(player);
				knowledge.setResearchFlag("FLUX", thaumcraft.api.capabilities.IPlayerKnowledge.EnumResearchFlag.POPUP);
				knowledge.setResearchFlag("FLUX", thaumcraft.api.capabilities.IPlayerKnowledge.EnumResearchFlag.RESEARCH);
				knowledge.sync(player);
			}
			player.displayClientMessage(net.minecraft.network.chat.Component.translatable("research.FLUX.warn").withStyle(net.minecraft.ChatFormatting.DARK_PURPLE), true);
		}
		thaumcraft.common.lib.network.ClientboundAuraPacket.sendLocal(player);
	}

	/**
	 * The original's {@code EntityUtils#getPointedEntity}: the nearest entity along the player's view within {@code range}, unless a block is in
	 * the way. Each entity's box is widened by at least 0.8 blocks for the test, which is what makes small things - a dropped item, an
	 * experience orb - possible to aim at (the exact hitbox of an item is a quarter of a block, and aiming at it that precisely is not).
	 */
	@javax.annotation.Nullable
	public static Entity pointedEntity(Level level, Player player, double range, float padding) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 end = eye.add(look.scale(range));
		AABB search = new AABB(eye, eye).inflate(0.5).expandTowards(look.scale(range)).inflate(padding);
		Entity best = null;
		double bestDistance = -1.0;
		for (Entity entity : level.getEntities(player, search, e -> !e.isSpectator())) {
			if (eye.distanceTo(entity.position()) < 1.0) continue;
			Vec3 entityEye = new Vec3(entity.getX(), entity.getY() + entity.getEyeHeight(), entity.getZ());
			if (level.clip(new ClipContext(eye, entityEye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS) continue;
			AABB box = entity.getBoundingBox().inflate(Math.max(0.8f, entity.getPickRadius()));
			if (box.contains(eye)) {
				if (bestDistance < 0.0 || bestDistance > 0.0) {
					best = entity;
					bestDistance = 0.0;
				}
			} else {
				var hit = box.clip(eye, end);
				if (hit.isPresent()) {
					double distance = eye.distanceTo(hit.get());
					if (bestDistance < 0.0 || distance < bestDistance) {
						best = entity;
						bestDistance = distance;
					}
				}
			}
		}
		return best;
	}

	/** Client half of a scan: the scan sound, and the rune burst on whatever is being scanned. */
	private static void scanEffects(Level level, Player player, @javax.annotation.Nullable Entity target, @javax.annotation.Nullable net.minecraft.core.BlockPos block) {
		level.playLocalSound(player.getX(), player.getY(), player.getZ(), thaumcraft.common.registry.ThaumcraftSounds.SCAN.get(), SoundSource.PLAYERS, 0.5f, 1.0f, false);
		if (target != null) {
			for (int a = 0; a < 10; a++) {
				thaumcraft.client.fx.ClientFxHandlers.spawnBlockRunes(level, target.getX() - 0.5, target.getY() + target.getEyeHeight() / 2.0f, target.getZ() - 0.5,
						0.3f + level.random.nextFloat() * 0.7f, 0.0f, 0.3f + level.random.nextFloat() * 0.7f, (int) (target.getBbHeight() * 15.0f), 0.03f);
			}
		} else if (block != null) {
			for (int a = 0; a < 10; a++) {
				thaumcraft.client.fx.ClientFxHandlers.spawnBlockRunes(level, block.getX(), block.getY() + 0.25, block.getZ(),
						0.3f + level.random.nextFloat() * 0.7f, 0.0f, 0.3f + level.random.nextFloat() * 0.7f, 15, 0.03f);
			}
		}
	}

	/** An entity being pointed at takes priority over the block it happens to be in front of (a dropped item lies ON a block, so a crosshair
	 * on it hits the floor - scanning the block instead was why an item could only be scanned by aiming above it, 2026-09-26). */
	@Override
	public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
		if (player.level().isClientSide) {
			scanEffects(player.level(), player, target, null);
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
		Entity pointed = pointedEntity(level, player, 9.0, 0.0f);
		if (level.isClientSide) {
			scanEffects(level, player, pointed, pointed == null ? context.getClickedPos() : null);
			return InteractionResult.SUCCESS;
		}
		if (pointed != null) {
			ScanningManager.scanTheThing(player, pointed);
		} else {
			ScanningManager.scanTheThing(player, context.getClickedPos());
		}
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
			scanEffects(level, player, target, target == null && block.getType() == HitResult.Type.BLOCK ? block.getBlockPos() : null);
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
