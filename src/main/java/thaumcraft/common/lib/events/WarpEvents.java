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
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.capabilities.IPlayerWarp;
import thaumcraft.api.capabilities.IPlayerWarp.EnumWarpType;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.items.IWarpingGear;
import thaumcraft.api.items.WornGearHelper;
import thaumcraft.common.config.ThaumcraftConfig;
import thaumcraft.common.items.armor.ItemFortressArmor;
import thaumcraft.common.registry.ThaumcraftEntities;
import thaumcraft.common.registry.ThaumcraftMobEffects;

/**
 * Replaces the old (1.12.2) {@code WarpEvents} - the periodic "Warp misfortune" system, as the original: every warp
 * event sends the heartbeat (unless the player's {@code nostress} option is on), the two mildest tiers play the
 * creeper hiss / distant explosion, the mist tiers thicken the player's fog and bring Eldritch Guardians, and the
 * curses can't be drunk away with milk. The screen effects of the curses are client-side
 * ({@link thaumcraft.client.lib.WarpScreenEffects}).
 * {@code getWarpFromGear} is rebuilt around {@link WornGearHelper} (hotbar/offhand) plus armor, replacing
 * the original's Baubles-slot scan - same real substitution this session's whole bauble-adaptation pass
 * already established.
 *
 * {@link ThaumcraftConfig#WUSS_MODE} is now honored for real (in {@link #checkWarpEvent}) - matching the
 * original's own {@code !ModConfig.CONFIG_MISC.wussMode} gate around this same misfortune roll.
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
		if (ThaumcraftConfig.WUSS_MODE.get()) return;
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

		if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
			thaumcraft.common.lib.network.ClientboundMiscEventPacket.send(sp, thaumcraft.common.lib.network.ClientboundMiscEventPacket.HEARTBEAT);
		}
		// The two mildest tiers are scare sounds. As in the original they're played to everyone nearby *except* the
		// warped player themselves (Level#playSound with a player excludes that player), and skipped under nostress.
		boolean stress = !ThaumcraftConfig.client(ThaumcraftConfig.NO_STRESS);
		if (eff <= 0) {
			// Nothing else to do - the original still ran the research-unlock checks below unconditionally.
		} else if (eff <= 4) {
			if (stress) {
				player.level().playSound(player, player.blockPosition(), net.minecraft.sounds.SoundEvents.CREEPER_PRIMED, net.minecraft.sounds.SoundSource.AMBIENT, 1.0f, 0.5f);
			}
		} else if (eff <= 8) {
			if (stress) {
				player.level().playSound(player, player.getX() + (random.nextFloat() - random.nextFloat()) * 10.0f,
						player.getY() + (random.nextFloat() - random.nextFloat()) * 10.0f, player.getZ() + (random.nextFloat() - random.nextFloat()) * 10.0f,
						net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.AMBIENT, 4.0f,
						(1.0f + (random.nextFloat() - random.nextFloat()) * 0.2f) * 0.7f);
			}
		} else if (eff <= 12) {
			message(player, "warp.text.11");
		} else if (eff <= 16) {
			player.addEffect(curse(ThaumcraftMobEffects.VIS_EXHAUST, 5000, Math.min(3, warp / 15)));
			message(player, "warp.text.1");
		} else if (eff <= 20) {
			player.addEffect(curse(ThaumcraftMobEffects.THAUMARHIA, Math.min(32000, 10 * warp), 0));
			message(player, "warp.text.15");
		} else if (eff <= 24) {
			player.addEffect(curse(ThaumcraftMobEffects.UNNATURAL_HUNGER, 5000, Math.min(3, warp / 15)));
			message(player, "warp.text.2");
		} else if (eff <= 28) {
			message(player, "warp.text.12");
		} else if (eff <= 32) {
			spawnMist(player, 1);
		} else if (eff <= 36) {
			player.addEffect(new MobEffectInstance(ThaumcraftMobEffects.BLURRED_VISION, Math.min(32000, 10 * warp), 0, true, true));
		} else if (eff <= 40) {
			player.addEffect(curse(ThaumcraftMobEffects.SUN_SCORNED, 5000, Math.min(3, warp / 15)));
			message(player, "warp.text.5");
		} else if (eff <= 44) {
			player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 1200, Math.min(3, warp / 15), true, true));
			message(player, "warp.text.9");
		} else if (eff <= 48) {
			player.addEffect(ThaumcraftMobEffects.milkProof(new MobEffectInstance(ThaumcraftMobEffects.INFECTIOUS_VIS_EXHAUST, 6000, Math.min(3, warp / 15))));
			message(player, "warp.text.1");
		} else if (eff <= 52) {
			player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, Math.min(40 * warp, 6000), 0, true, true));
			message(player, "warp.text.10");
		} else if (eff <= 56) {
			player.addEffect(curse(ThaumcraftMobEffects.DEATH_GAZE, 6000, Math.min(3, warp / 15)));
			message(player, "warp.text.4");
		} else if (eff <= 60) {
			suddenlySpiders(player, warp, false);
		} else if (eff <= 64) {
			message(player, "warp.text.13");
		} else if (eff <= 68) {
			spawnMist(player, warp / 30);
		} else if (eff <= 72) {
			player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, Math.min(32000, 5 * warp), 0, true, true));
		} else if (eff == 76) {
			if (nw > 0) {
				ThaumcraftApi.internalMethods.addWarpToPlayer(player, -1, EnumWarpType.NORMAL);
			}
			message(player, "warp.text.14");
		} else if (eff <= 80) {
			player.addEffect(curse(ThaumcraftMobEffects.UNNATURAL_HUNGER, 6000, Math.min(3, warp / 15)));
			message(player, "warp.text.2");
		} else if (eff <= 88) {
			spawnPortal(player);
		} else if (eff <= 92) {
			suddenlySpiders(player, warp, true);
		} else {
			spawnMist(player, warp / 15);
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

	/** A warp curse as the original applied it: ambient, with particles, and immune to milk. */
	private static MobEffectInstance curse(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int duration, int amplifier) {
		return ThaumcraftMobEffects.milkProof(new MobEffectInstance(effect, duration, amplifier, true, true));
	}

	/** The original's spawnMist: the player's fog thickens for two minutes and up to 8 Eldritch Guardians come. */
	private static void spawnMist(Player player, int guardians) {
		if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
			thaumcraft.common.lib.network.ClientboundMiscEventPacket.send(sp, thaumcraft.common.lib.network.ClientboundMiscEventPacket.MIST);
		}
		for (int a = 0; a < Math.min(8, guardians); a++) {
			spawnGuardian(player);
		}
		message(player, "warp.text.6");
	}

	private static void spawnGuardian(Player player) {
		if (!(player.level() instanceof ServerLevel level)) return;
		BlockPos origin = player.blockPosition();
		for (int l = 0; l < 50; l++) {
			BlockPos candidate = randomOffset(level.random, origin);
			if (!level.getBlockState(candidate.below()).isCollisionShapeFullBlock(level, candidate.below())) continue;
			var guardian = ThaumcraftEntities.ELDRITCH_GUARDIAN.get().create(level);
			if (guardian == null) return;
			guardian.moveTo(candidate.getX(), candidate.getY(), candidate.getZ(), 0, 0);
			if (level.noCollision(guardian) && !level.containsAnyLiquid(guardian.getBoundingBox())) {
				guardian.setTarget(player);
				level.addFreshEntity(guardian);
				return;
			}
		}
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

	/** The original's {@code checkDeathGaze}: under Death Gaze, every living thing the player looks at (within a
	 * 0.75-radian cone, in range and in sight; other players only with PvP on; ones already withering skipped) turns on
	 * the player and withers. */
	public static void checkDeathGaze(Player player) {
		MobEffectInstance pe = player.getEffect(ThaumcraftMobEffects.DEATH_GAZE);
		if (pe == null) return;
		int level = pe.getAmplifier();
		double range = Math.min(8 + level * 3, 24);
		List<Entity> nearby = player.level().getEntities(player, player.getBoundingBox().inflate(range));
		for (Entity entity : nearby) {
			if (!entity.isPickable() || !(entity instanceof LivingEntity living) || !living.isAlive()) continue;
			if (!isVisibleTo(0.75f, player, entity, (float) range) || !player.hasLineOfSight(entity)) continue;
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

	/** The original's EntityUtils#isVisibleTo: whether {@code target}'s middle lies in a cone of {@code fov} radians
	 * (full aperture) along {@code viewer}'s look, no further along it than {@code range}. */
	static boolean isVisibleTo(float fov, Entity viewer, Entity target, float range) {
		net.minecraft.world.phys.Vec3 apex = new net.minecraft.world.phys.Vec3(viewer.getX(), viewer.getBoundingBox().minY + viewer.getEyeHeight(), viewer.getZ());
		net.minecraft.world.phys.Vec3 toTarget = new net.minecraft.world.phys.Vec3(target.getX(), target.getBoundingBox().minY + target.getBbHeight() / 2.0f, target.getZ()).subtract(apex);
		net.minecraft.world.phys.Vec3 axis = viewer.getLookAngle().scale(range);
		double dot = toTarget.dot(axis);
		if (dot / toTarget.length() / axis.length() <= Math.cos(fov / 2.0f)) return false;
		return dot / axis.length() < axis.length();
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

	/**
	 * The real warp-on-craft penalty, the last live mechanic out of the old (1.12.2) {@code CraftingEvents}
	 * (its research-hash half is now {@link ResearchCraftingEvents}). Crafting an item registered via
	 * {@link ThaumcraftApi#addWarpToItem} costs that much real permanent warp, gated on the real
	 * {@code wussMode} config exactly as the original gated it.
	 *
	 * <p>Worth recording how this got here, because an earlier pass in this port concluded the opposite and
	 * was wrong: the note then was that warp-on-craft must be a decompilation artifact, since the penalty
	 * reads {@code ThaumcraftApi.getWarp(ItemStack)} and nothing ever writes the {@code TC.WARP} NBT tag.
	 * Reading the original directly disproved it - {@code getWarp} never looked at {@code TC.WARP} at all.
	 * It reads {@link thaumcraft.api.internal.CommonInternals#warpMap}, a static item→warp registry, and
	 * {@code TC.WARP} is an entirely separate mechanism (worn-gear warp, written by the original's own
	 * {@code InfusionEnchantmentRecipe}). Two different systems that happen to both be called "warp".
	 *
	 * <p>The mechanic is real but deliberately sparse: shipped Thaumcraft 6 calls {@code addWarpToItem}
	 * exactly once, for the Brain in a Jar at 1 warp (its own {@code ConfigResearch} line 282). That single
	 * registration lives in {@code Thaumcraft}'s setup here. It is also distinct from the warp a research
	 * <i>entry</i> carries, which is a separate real path through {@code ResearchStage#getWarp}.
	 */
	@SubscribeEvent
	public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
		Player player = event.getEntity();
		if (player == null || player.level().isClientSide || ThaumcraftConfig.WUSS_MODE.get()) return;
		int warp = ThaumcraftApi.getWarp(event.getCrafting());
		if (warp > 0) {
			ThaumcraftApi.internalMethods.addWarpToPlayer(player, warp, EnumWarpType.NORMAL);
		}
	}
}
