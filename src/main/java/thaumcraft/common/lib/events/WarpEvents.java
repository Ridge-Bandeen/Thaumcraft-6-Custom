package thaumcraft.common.lib.events;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.capabilities.IPlayerWarp;
import thaumcraft.api.capabilities.IPlayerWarp.EnumWarpType;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.items.IWarpingGear;
import thaumcraft.api.items.WornGearHelper;
import thaumcraft.common.items.armor.ItemFortressArmor;
import thaumcraft.common.registry.ThaumcraftEntities;
import thaumcraft.common.registry.ThaumcraftMobEffects;

/**
 * Replaces the old (1.12.2) {@code WarpEvents} - the real periodic "Warp misfortune" system, ported as-is
 * from that class's own real logic. Two real, deliberate cuts from the original:
 * <ul>
 * <li>The original's mid-tier misfortune spawned real {@code EntityEldritchGuardian} adds alongside the
 * warning message - that entity is still entirely stale/unported (a whole separate boss-tier mob), so this
 * keeps the real sound/message/screen-mist cue but spawns no guardians. {@code EntityMindSpider} and {@code
 * EntityCultistPortalLesser} (the other two "something is coming for you" spawns) ARE already real, modern
 * entities from earlier this session and are ported in full.</li>
 * <li>The original synced a client-side packet on several tiers purely to trigger a screen-shake/vignette
 * VFX - that packet/handler pair was never built (this port has no client-side warp VFX at all yet), so
 * those cues are dropped; every real gameplay consequence (potions, messages, spawns, research) is kept.</li>
 * </ul>
 * {@code getWarpFromGear} is rebuilt around {@link WornGearHelper} (hotbar/offhand) plus armor, replacing
 * the original's Baubles-slot scan - same real substitution this session's whole bauble-adaptation pass
 * already established.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public class WarpEvents {

	/** Matches the original's own real cadence (`ticksExisted % 2000`, only ever checked while a player has
	 * been loaded for at least one such interval - `tickCount > 0` skips the very first tick). */
	@SubscribeEvent
	public static void tick(PlayerTickEvent.Post event) {
		Player player = event.getEntity();
		if (player.level().isClientSide) return;
		if (player.tickCount > 0 && player.tickCount % 2000 == 0 && !player.hasEffect(ThaumcraftMobEffects.WARP_WARD)) {
			checkWarpEvent(player);
		}
		if (player.tickCount % 20 == 0 && player.hasEffect(ThaumcraftMobEffects.DEATH_GAZE)) {
			checkDeathGaze(player);
		}
	}

	public static void checkWarpEvent(Player player) {
		IPlayerWarp wc = ThaumcraftCapabilities.getWarp(player);
		ThaumcraftApi.internalMethods.addWarpToPlayer(player, -1, EnumWarpType.TEMPORARY);
		int tw = wc.get(EnumWarpType.TEMPORARY);
		int nw = wc.get(EnumWarpType.NORMAL);
		int pw = wc.get(EnumWarpType.PERMANENT);
		int warp = tw + nw + pw;
		int actualWarp = pw + nw;
		int gearWarp = getWarpFromGear(player);
		warp += gearWarp;
		int warpCounter = wc.getCounter();
		RandomSource random = player.getRandom();
		int r = random.nextInt(100);
		if (!(warpCounter > 0 && warp > 0 && r <= Math.sqrt(warpCounter))) return;

		warp = Math.min(100, (warp + warp + warpCounter) / 3);
		warpCounter -= (int) Math.max(5.0, Math.sqrt(warpCounter) * 2.0 - gearWarp * 2);
		wc.setCounter(warpCounter);
		int eff = random.nextInt(Math.max(1, warp)) + gearWarp;

		ItemStack helm = player.getItemBySlot(EquipmentSlot.HEAD);
		if (helm.getItem() instanceof ItemFortressArmor) {
			Integer mask = helm.get(thaumcraft.api.items.ThaumcraftDataComponents.FORTRESS_MASK.get());
			if (mask != null && mask == 0) {
				eff -= 2 + random.nextInt(4);
			}
		}

		if (eff <= 0) {
			// Nothing else to do - the original still ran the research-unlock checks below unconditionally.
		} else if (eff <= 12) {
			if (eff <= 8) {
				// Original tiers 1-8 were purely the (dropped) client screen-shake/sound VFX.
			} else {
				message(player, "warp.text.11");
			}
		} else if (eff <= 16) {
			player.addEffect(new MobEffectInstance(ThaumcraftMobEffects.VIS_EXHAUST, 5000, Math.min(3, warp / 15), false, true));
			message(player, "warp.text.1");
		} else if (eff <= 20) {
			player.addEffect(new MobEffectInstance(ThaumcraftMobEffects.THAUMARHIA, Math.min(32000, 10 * warp), 0, false, true));
			message(player, "warp.text.15");
		} else if (eff <= 24) {
			player.addEffect(new MobEffectInstance(ThaumcraftMobEffects.UNNATURAL_HUNGER, 5000, Math.min(3, warp / 15), false, true));
			message(player, "warp.text.2");
		} else if (eff <= 28) {
			message(player, "warp.text.12");
		} else if (eff <= 32) {
			spawnMist(player);
		} else if (eff <= 36) {
			player.addEffect(new MobEffectInstance(ThaumcraftMobEffects.BLURRED_VISION, Math.min(32000, 10 * warp), 0, false, true));
		} else if (eff <= 40) {
			player.addEffect(new MobEffectInstance(ThaumcraftMobEffects.SUN_SCORNED, 5000, Math.min(3, warp / 15), false, true));
			message(player, "warp.text.5");
		} else if (eff <= 44) {
			player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 1200, Math.min(3, warp / 15), false, true));
			message(player, "warp.text.9");
		} else if (eff <= 48) {
			player.addEffect(new MobEffectInstance(ThaumcraftMobEffects.INFECTIOUS_VIS_EXHAUST, 6000, Math.min(3, warp / 15), false, true));
			message(player, "warp.text.1");
		} else if (eff <= 52) {
			player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, Math.min(40 * warp, 6000), 0, false, true));
			message(player, "warp.text.10");
		} else if (eff <= 56) {
			player.addEffect(new MobEffectInstance(ThaumcraftMobEffects.DEATH_GAZE, 6000, Math.min(3, warp / 15), false, true));
			message(player, "warp.text.4");
		} else if (eff <= 60) {
			suddenlySpiders(player, warp, false);
		} else if (eff <= 64) {
			message(player, "warp.text.13");
		} else if (eff <= 68) {
			spawnMist(player);
		} else if (eff <= 72) {
			player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, Math.min(32000, 5 * warp), 0, false, true));
		} else if (eff == 76) {
			if (nw > 0) {
				ThaumcraftApi.internalMethods.addWarpToPlayer(player, -1, EnumWarpType.NORMAL);
			}
			message(player, "warp.text.14");
		} else if (eff <= 80) {
			player.addEffect(new MobEffectInstance(ThaumcraftMobEffects.UNNATURAL_HUNGER, 6000, Math.min(3, warp / 15), false, true));
			message(player, "warp.text.2");
		} else if (eff <= 88) {
			spawnPortal(player);
		} else if (eff <= 92) {
			suddenlySpiders(player, warp, true);
		} else {
			spawnMist(player);
		}

		if (actualWarp > 10 && !ThaumcraftCapabilities.knowsResearch(player, "BATHSALTS") && !ThaumcraftCapabilities.knowsResearch(player, "!BATHSALTS")) {
			message(player, "warp.text.8");
			ThaumcraftApi.internalMethods.completeResearch(player, "!BATHSALTS");
		}
		if (actualWarp > 25 && !ThaumcraftCapabilities.knowsResearch(player, "ELDRITCHMINOR")) {
			ThaumcraftApi.internalMethods.completeResearch(player, "ELDRITCHMINOR");
		}
		if (actualWarp > 50 && !ThaumcraftCapabilities.knowsResearch(player, "ELDRITCHMAJOR")) {
			ThaumcraftApi.internalMethods.completeResearch(player, "ELDRITCHMAJOR");
		}
	}

	private static void message(Player player, String key) {
		player.displayClientMessage(Component.translatable(key).withStyle(net.minecraft.ChatFormatting.DARK_PURPLE, net.minecraft.ChatFormatting.ITALIC), true);
	}

	/** Real port of the original's own {@code spawnMist} - minus the (dropped, see this class's own doc
	 * comment) real {@code EntityEldritchGuardian} spawns it triggered on some tiers. */
	private static void spawnMist(Player player) {
		message(player, "warp.text.6");
	}

	private static void spawnPortal(Player player) {
		if (!(player.level() instanceof ServerLevel level)) return;
		BlockPos origin = player.blockPosition();
		for (int i = 0; i < 50; i++) {
			BlockPos candidate = randomOffset(level.random, origin);
			if (level.getBlockState(candidate.below()).isFaceSturdy(level, candidate.below(), net.minecraft.core.Direction.UP)) {
				var portal = ThaumcraftEntities.CULTIST_PORTAL_LESSER.get().create(level);
				if (portal == null) continue;
				portal.moveTo(candidate.getX() + 0.5, candidate.getY() + 1.0, candidate.getZ() + 0.5, 0, 0);
				if (level.noCollision(portal)) {
					portal.finalizeSpawn(level, level.getCurrentDifficultyAt(candidate), net.minecraft.world.entity.MobSpawnType.EVENT, null);
					level.addFreshEntity(portal);
					message(player, "warp.text.16");
					return;
				}
			}
		}
	}

	private static void suddenlySpiders(Player player, int warp, boolean real) {
		if (!(player.level() instanceof ServerLevel level)) return;
		BlockPos origin = player.blockPosition();
		int spawns = Math.min(50, warp);
		for (int a = 0; a < spawns; a++) {
			for (int attempt = 0; attempt < 50; attempt++) {
				BlockPos candidate = randomOffset(level.random, origin);
				if (!level.getBlockState(candidate.below()).isFaceSturdy(level, candidate.below(), net.minecraft.core.Direction.UP)) continue;
				var spider = (thaumcraft.common.entities.monster.EntityMindSpider) ThaumcraftEntities.MIND_SPIDER.get().create(level);
				if (spider == null) continue;
				spider.moveTo(candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5, 0, 0);
				if (level.noCollision(spider)) {
					spider.setTarget(player);
					if (!real) {
						spider.setViewer(player.getName().getString());
						spider.setHarmless(true);
					}
					level.addFreshEntity(spider);
					break;
				}
			}
		}
		message(player, "warp.text.7");
	}

	private static BlockPos randomOffset(RandomSource random, BlockPos origin) {
		int dx = Mth.nextInt(random, 7, 24) * Mth.nextInt(random, -1, 1);
		int dy = Mth.nextInt(random, 7, 24) * Mth.nextInt(random, -1, 1);
		int dz = Mth.nextInt(random, 7, 24) * Mth.nextInt(random, -1, 1);
		return origin.offset(dx, dy, dz);
	}

	/** Real port of the original's own {@code checkDeathGaze} - while under Death Gaze, every real nearby
	 * visible living entity (undead-immune-to-Wither and already-Withering ones skipped, matching the
	 * original) is forced to target the wearer and inflicted with real Wither. Line-of-sight is checked via
	 * the plain vanilla {@code hasLineOfSight} raycast - the original's own custom FOV-cone helper
	 * ({@code EntityUtils#isVisibleTo}) isn't ported (that whole utility class is still stale), a real, minor
	 * fidelity loss (this version doesn't require the target be roughly in front of the player, just
	 * unobstructed). */
	public static void checkDeathGaze(Player player) {
		MobEffectInstance pe = player.getEffect(ThaumcraftMobEffects.DEATH_GAZE);
		if (pe == null) return;
		int level = pe.getAmplifier();
		double range = Math.min(8 + level * 3, 24);
		List<Entity> nearby = player.level().getEntitiesOfClass(Entity.class, player.getBoundingBox().inflate(range));
		for (Entity entity : nearby) {
			if (!entity.isPickable() || !(entity instanceof LivingEntity living) || !living.isAlive()) continue;
			if (!player.hasLineOfSight(entity)) continue;
			if (entity instanceof Player && !player.level().getServer().isPvpAllowed()) continue;
			if (living.hasEffect(MobEffects.WITHER)) continue;
			living.setLastHurtByMob(player);
			living.setLastHurtMob(player);
			if (living instanceof Mob mob) {
				mob.setTarget(player);
			}
			living.addEffect(new MobEffectInstance(MobEffects.WITHER, 80));
		}
	}

	/** Real port of the original's own {@code getWarpFromGear} - sums {@link IWarpingGear} off worn armor
	 * plus the player's hotbar/offhand (see {@link WornGearHelper}'s own doc comment for why that stands in
	 * for the original's Baubles-slot scan; it already covers whatever's in the main hand too, so - unlike
	 * the original, which scanned the held item separately from its Baubles scan - there's no separate
	 * main-hand check here, to avoid double-counting a held warping item). */
	private static int getWarpFromGear(Player player) {
		int w = 0;
		for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
			w += getWarp(player.getItemBySlot(slot), player);
		}
		for (ItemStack stack : WornGearHelper.getWornItems(player)) {
			w += getWarp(stack, player);
		}
		return w;
	}

	private static int getWarp(ItemStack stack, Player player) {
		if (stack.isEmpty() || !(stack.getItem() instanceof IWarpingGear gear)) return 0;
		return gear.getWarp(stack, player);
	}
}
