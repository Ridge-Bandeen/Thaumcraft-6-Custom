package thaumcraft.common.lib.events;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectHelper;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.ItemGenericEssentiaContainer;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.entities.EntityFollowingItem;
import thaumcraft.common.lib.enchantment.EnumInfusionEnchantment;
import thaumcraft.common.lib.network.fx.PacketFXScanSource;
import thaumcraft.common.lib.network.fx.PacketFXSlash;

/**
 * Replaces the old (1.12.2) {@code ToolEvents}. Only the two infusion-enchantment gameplay hooks reachable
 * without any not-yet-ported infrastructure are ported this pass:
 * <ul>
 * <li>{@link EnumInfusionEnchantment#LAMPLIGHT} - places a real light source in a mined-out dark spot. The
 * original placed a custom invisible {@code BlockEffect} variant ({@code effectGlimmer}); vanilla's own
 * {@link Blocks#LIGHT} (also invisible, also a plain light source) does the same job with nothing new to
 * port.</li>
 * <li>{@link EnumInfusionEnchantment#DESTRUCTIVE} - chain-breaks the 8 neighbours in the plane of the block
 * just mined. {@link #LAST_FACE_CLICKED} reproduces the original's own face-tracking
 * ({@code PlayerInteractEvent.LeftClickBlock} to {@code BlockEvent.HarvestDropsEvent}, now
 * {@link BlockDropsEvent}), and {@code level.destroyBlock(pos, true, harvester, depth)} replaces the
 * original's manual {@code BlockUtils.harvestBlock} - vanilla's own method already drops items and plays
 * sounds correctly, no hand-rolled harvest helper needed.</li>
 * <li>{@link EnumInfusionEnchantment#COLLECTOR} - redirects both harvest drops and kill drops to home in on
 * the player instead of falling normally, via {@link EntityFollowingItem}. The original used two different
 * mechanisms for this (a dedicated {@code InventoryUtils.dropHarvestsAtPos} helper for the harvest side,
 * direct {@code EntityFollowingItem} wrapping for the kill side) - unified here into one
 * {@link #redirectToHomeOn} used both places, since {@link BlockDropsEvent}/{@link LivingDropsEvent} both
 * already hand over a mutable list of not-yet-spawned {@code ItemEntity}s to replace in place.</li>
 * <li>{@link EnumInfusionEnchantment#ESSENCE} - on a kill, drops up to {@code level} essentia crystals drawn
 * from the victim's own {@link thaumcraft.api.aspects.AspectHelper#getEntityAspects}, each a real crystal
 * (see {@link #makeCrystal}) removed one unit at a time from a copy of the victim's aspect pool so the same
 * aspect isn't drawn more than it actually had. Interops with {@code COLLECTOR} automatically if the killer
 * also has it (the crystal is added post-{@link #redirectToHomeOn}, as a real homing entity, not a plain
 * drop) - simplified from the original's own parallel "if COLLECTOR, make it a following item too" branch,
 * same outcome either way.</li>
 * <li>{@link EnumInfusionEnchantment#REFINING} - a fortune-like chance (scaling with level, same
 * {@code (1 + level) * 0.125f} formula the original used) to replace a mined drop with the matching
 * {@code cluster_*} item instead - see {@link #SPECIAL_MINING}. The original's version of this table
 * (Forge's pre-1.13 ore-dictionary) doesn't exist anymore, but it's not needed: the original only ever
 * registered 4 entries (iron/gold/cinnabar/quartz), each keyed on one specific drop, so a plain
 * {@code Item -> ItemStack} map covers it exactly - no tag matching required.</li>
 * <li>{@link EnumInfusionEnchantment#ARCING} - cleaves up to {@code level} nearby living entities for half
 * the player's attack damage whenever they land a hit, real for the first time this port has a working
 * client packet ({@link PacketFXSlash}, see {@code Thaumcraft#registerPayloads}) - the visual cue (a small
 * particle burst at each secondary hit, plus one more at the primary target) rides along it. No "friendly
 * fire" check (the original's own {@code EntityUtils.isFriendly} isn't ported) - any nearby
 * {@link LivingEntity} other than the primary target and the attacker themself is fair game, a real,
 * documented simplification.</li>
 * <li>{@link EnumInfusionEnchantment#SOUNDING} - damages the tool, plays a resonating sound, and sends a
 * targeted {@code PacketFXScanSource} to the scanning player on a sneak right-click, matching the original's
 * own server-side behaviour exactly. The actual scan (reading nearby block state, grouping connected ore
 * blocks, colouring each group) happens entirely client-side in
 * {@link thaumcraft.client.fx.ClientFxHandlers#handleScanSource} - a vanilla colored-dust particle burst per
 * group replaces the original's custom {@code FXGeneric} billboard particle, and every group reveals at once
 * instead of the original's distance-staggered "sonar ping" timing (a real, documented simplification).</li>
 * <li>{@link EnumInfusionEnchantment#BURROWING} - auto-tunnels toward the furthest still-standing block of
 * the same type connected to the one just mined ({@link #findFurthestMatchingBlock}), a plain bounded
 * breadth-first search replacing the original's own recursive "restart the whole cube scan from the new best
 * match" approach in {@code BlockUtils.findBlocks} - same real search (matches by block type, diagonal
 * connections count, a wood log gets a wider reach than an ore block), a clearer/cheaper way to run it.
 * {@code level.destroyBlock} (already proven by {@code DESTRUCTIVE}) covers the original's own
 * {@code BlockUtils.harvestBlock} - that whole hand-rolled helper turns out to just be a 1.12.2-era
 * reimplementation of what {@code ServerPlayerGameMode#destroyBlock} already does natively now.
 * <p>
 * One real behavioural difference from the original, forced by which event this hooks: the original
 * canceled the actual clicked block's own break ({@code BlockEvent.BreakEvent}, fired before removal) and
 * harvested only the vein's tip instead - a "redirect the mine" effect. This hooks {@link BlockDropsEvent}
 * (fired after removal, same as every other enchantment here) instead, which can't cancel anything - so this
 * version mines the clicked block normally *and*, as a bonus, the vein's tip too, rather than redirecting to
 * just the tip. A real difference, not a bug - both are "auto-tunnel toward the end of the vein", this one is
 * just additive instead of substitutive.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public class ToolEvents {

	private static final Map<UUID, Direction> LAST_FACE_CLICKED = new HashMap<>();
	private static boolean destructiveRecursion;
	private static boolean burrowingRecursion;

	/** How far (in blocks, each axis) BURROWING's vein-walk is allowed to search from the block actually
	 * mined, and how many blocks it's allowed to visit doing so - both real caps the original also had
	 * (~24/48/24), the second one a modern addition (the original's own restart-the-whole-cube-scan approach
	 * had no hard visited-node bound of its own, relying on the position caps alone). */
	private static final int BURROW_MAX_RADIUS = 24;
	private static final int BURROW_MAX_VISITED = 4096;

	/** REFINING's real target table - see this class's own doc comment. Populated by {@link #initSpecialMining}
	 * (called from {@code Thaumcraft#commonSetup}, not a static initializer - the same
	 * {@code DeferredHolder#get()}-before-{@code RegisterEvent} timing rule {@code ThaumcraftObjectTags}/
	 * {@code ThaumcraftEntityTags} already ran into applies here too). */
	private static final Map<Item, ItemStack> SPECIAL_MINING = new HashMap<>();

	public static void initSpecialMining() {
		SPECIAL_MINING.put(Items.RAW_IRON, new ItemStack(ItemsTC.clusterIron.get()));
		SPECIAL_MINING.put(Items.RAW_GOLD, new ItemStack(ItemsTC.clusterGold.get()));
		SPECIAL_MINING.put(BlocksTC.oreCinnabar.get().asItem(), new ItemStack(ItemsTC.clusterCinnabar.get()));
		SPECIAL_MINING.put(Items.QUARTZ, new ItemStack(ItemsTC.clusterQuartz.get()));
	}

	@SubscribeEvent
	public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
		if (!event.getLevel().isClientSide) {
			LAST_FACE_CLICKED.put(event.getEntity().getUUID(), event.getFace());
		}
	}

	@SubscribeEvent
	public static void onAttackEntity(AttackEntityEvent event) {
		if (!(event.getEntity() instanceof Player attacker) || !(attacker.level() instanceof ServerLevel level)) {
			return;
		}
		ItemStack tool = attacker.getMainHandItem();
		if (tool.isEmpty() || !EnumInfusionEnchantment.getInfusionEnchantments(tool).contains(EnumInfusionEnchantment.ARCING)) {
			return;
		}
		if (!(event.getTarget() instanceof LivingEntity primary) || !primary.isAlive()) {
			return;
		}

		int rank = EnumInfusionEnchantment.getInfusionEnchantmentLevel(tool, EnumInfusionEnchantment.ARCING);
		AABB area = primary.getBoundingBox().inflate(1.5 + rank, 1.0 + rank / 2.0, 1.5 + rank);
		List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, area,
				e -> e != primary && e != attacker && e.isAlive());

		float damage = (float) attacker.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.5f;
		DamageSource source = level.damageSources().playerAttack(attacker);
		int count = 0;
		for (LivingEntity secondary : nearby) {
			if (count >= rank) {
				break;
			}
			if (secondary.hurt(source, damage)) {
				count++;
				sendSlashFx(level, primary.getX(), primary.getY(), primary.getZ(), primary.getId(), secondary.getId());
			}
		}
		if (count > 0) {
			level.playSound(null, attacker.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f,
					0.9f + level.getRandom().nextFloat() * 0.2f);
			sendSlashFx(level, attacker.getX(), attacker.getY(), attacker.getZ(), attacker.getId(), primary.getId());
		}
	}

	/** The cleave itself (real damage, already applied by the caller) never depends on this succeeding - a
	 * purely cosmetic packet failing to send (e.g. a GameTest mock player, which has no real client
	 * connection to receive it) should never take the actual gameplay effect down with it. */
	private static void sendSlashFx(ServerLevel level, double x, double y, double z, int source, int target) {
		try {
			PacketDistributor.sendToPlayersNear(level, null, x, y, z, 64.0, new PacketFXSlash(source, target));
		} catch (Exception ex) {
			Thaumcraft.LOGGER.debug("Couldn't send fx_slash packet", ex);
		}
	}

	@SubscribeEvent
	public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		if (!(event.getEntity() instanceof Player player) || !(player.level() instanceof ServerLevel level) || !player.isCrouching()) {
			return;
		}
		ItemStack tool = event.getItemStack();
		if (tool.isEmpty() || !EnumInfusionEnchantment.getInfusionEnchantments(tool).contains(EnumInfusionEnchantment.SOUNDING)) {
			return;
		}
		tool.hurtAndBreak(5, player, EquipmentSlot.MAINHAND);
		BlockPos pos = event.getPos();
		level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.2f,
				0.2f + level.getRandom().nextFloat() * 0.2f);
		if (player instanceof ServerPlayer serverPlayer) {
			int rank = EnumInfusionEnchantment.getInfusionEnchantmentLevel(tool, EnumInfusionEnchantment.SOUNDING);
			try {
				PacketDistributor.sendToPlayer(serverPlayer, new PacketFXScanSource(pos, rank));
			} catch (Exception ex) {
				Thaumcraft.LOGGER.debug("Couldn't send fx_scan_source packet", ex);
			}
		}
	}

	@SubscribeEvent
	public static void onBlockDrops(BlockDropsEvent event) {
		if (!(event.getBreaker() instanceof Player harvester) || harvester.isCrouching()) {
			return;
		}
		ItemStack tool = event.getTool();
		if (tool.isEmpty()) {
			return;
		}
		List<EnumInfusionEnchantment> enchantments = EnumInfusionEnchantment.getInfusionEnchantments(tool);
		if (enchantments.isEmpty()) {
			return;
		}
		ServerLevel level = event.getLevel();
		BlockPos pos = event.getPos();

		if (enchantments.contains(EnumInfusionEnchantment.LAMPLIGHT)) {
			tryPlaceLamplight(level, pos);
		}
		if (!destructiveRecursion && enchantments.contains(EnumInfusionEnchantment.DESTRUCTIVE)) {
			breakSurroundingBlocks(level, pos, harvester, tool);
		}
		if (!burrowingRecursion && enchantments.contains(EnumInfusionEnchantment.BURROWING)) {
			tryBurrow(level, pos, event.getState(), harvester, tool);
		}
		if (enchantments.contains(EnumInfusionEnchantment.COLLECTOR)) {
			redirectToHomeOn(event.getDrops(), harvester);
		}
		if (enchantments.contains(EnumInfusionEnchantment.REFINING)) {
			rollSpecialMining(event.getDrops(), EnumInfusionEnchantment.getInfusionEnchantmentLevel(tool, EnumInfusionEnchantment.REFINING), harvester.getRandom());
		}
	}

	private static void rollSpecialMining(List<ItemEntity> drops, int level, RandomSource rand) {
		float chance = (1 + level) * 0.125f;
		drops.replaceAll(drop -> {
			ItemStack template = SPECIAL_MINING.get(drop.getItem().getItem());
			if (template == null || rand.nextFloat() > chance) {
				return drop;
			}
			ItemStack replacement = template.copyWithCount(template.getCount() * drop.getItem().getCount());
			drop.setItem(replacement);
			return drop;
		});
	}

	@SubscribeEvent
	public static void onLivingDrops(LivingDropsEvent event) {
		if (!(event.getSource().getEntity() instanceof Player killer)) {
			return;
		}
		ItemStack tool = killer.getMainHandItem();
		if (tool.isEmpty()) {
			return;
		}
		List<EnumInfusionEnchantment> enchantments = EnumInfusionEnchantment.getInfusionEnchantments(tool);
		if (enchantments.isEmpty()) {
			return;
		}
		boolean collector = enchantments.contains(EnumInfusionEnchantment.COLLECTOR);
		if (collector && event.getDrops() instanceof List<ItemEntity> drops) {
			redirectToHomeOn(drops, killer);
		}
		if (enchantments.contains(EnumInfusionEnchantment.ESSENCE)) {
			dropEssenceCrystals(event, killer, tool, collector);
		}
	}

	/** Replaces every not-yet-spawned drop in {@code drops} with an {@link EntityFollowingItem} homing on
	 * {@code target}, in place - both {@link BlockDropsEvent#getDrops()} and
	 * {@link LivingDropsEvent#getDrops()} hand over their list before any entity in it has actually been
	 * added to the world, so replacing list entries (rather than discarding+re-adding real entities) is
	 * enough. */
	private static void redirectToHomeOn(List<ItemEntity> drops, Entity target) {
		drops.replaceAll(drop -> new EntityFollowingItem(drop.level(), drop.getX(), drop.getY(), drop.getZ(), drop.getItem(), target));
	}

	private static void dropEssenceCrystals(LivingDropsEvent event, Player killer, ItemStack tool, boolean collector) {
		LivingEntity victim = event.getEntity();
		AspectList entityAspects = AspectHelper.getEntityAspects(victim);
		if (entityAspects == null || entityAspects.size() <= 0) {
			return;
		}
		int level = EnumInfusionEnchantment.getInfusionEnchantmentLevel(tool, EnumInfusionEnchantment.ESSENCE);
		AspectList pool = entityAspects.copy();
		RandomSource rand = victim.getRandom();
		double x = victim.getX();
		double y = victim.getY() + victim.getEyeHeight();
		double z = victim.getZ();
		for (int i = 0; i < level && pool.size() > 0; i++) {
			Aspect[] available = pool.getAspects();
			Aspect chosen = available[rand.nextInt(available.length)];
			pool.remove(chosen, 1);
			ItemStack crystal = makeCrystal(chosen);
			event.getDrops().add(collector
					? new EntityFollowingItem(victim.level(), x, y, z, crystal, killer)
					: new ItemEntity(victim.level(), x, y, z, crystal));
		}
	}

	/** Small, local stand-in for the original's {@code ThaumcraftApiHelper.makeCrystal} - that whole class
	 * is still 1.12.2-era, unconverted, and not worth porting in full just for this one method. */
	private static ItemStack makeCrystal(Aspect aspect) {
		ItemStack stack = new ItemStack(ItemsTC.crystalEssence.get());
		((ItemGenericEssentiaContainer) stack.getItem()).setAspects(stack, new AspectList().add(aspect, 1));
		return stack;
	}

	private static void tryPlaceLamplight(ServerLevel level, BlockPos pos) {
		if (level.getBlockState(pos).isAir() && level.getMaxLocalRawBrightness(pos) < 10) {
			level.setBlock(pos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15), 3);
		}
	}

	private static void breakSurroundingBlocks(ServerLevel level, BlockPos pos, Player harvester, ItemStack tool) {
		Direction face = LAST_FACE_CLICKED.getOrDefault(harvester.getUUID(), Direction.UP);
		destructiveRecursion = true;
		try {
			for (int a = -1; a <= 1; a++) {
				for (int b = -1; b <= 1; b++) {
					if (a == 0 && b == 0) {
						continue;
					}
					BlockPos neighbor = planeOffset(pos, face, a, b);
					BlockState state = level.getBlockState(neighbor);
					if (state.isAir() || state.getDestroySpeed(level, neighbor) < 0) {
						continue;
					}
					if (!tool.isCorrectToolForDrops(state)) {
						continue;
					}
					tool.hurtAndBreak(1, harvester, EquipmentSlot.MAINHAND);
					level.destroyBlock(neighbor, true, harvester, 512);
				}
			}
		} finally {
			destructiveRecursion = false;
		}
	}

	/** The 3x3 (minus centre) plane perpendicular to the face the player last left-clicked - same shape as
	 * the original's own axis-swap-on-{@code face.ordinal()} logic. */
	private static BlockPos planeOffset(BlockPos center, Direction face, int a, int b) {
		return switch (face.getAxis()) {
			case Y -> center.offset(a, 0, b);
			case X -> center.offset(0, a, b);
			case Z -> center.offset(a, b, 0);
		};
	}

	/** Finds the furthest still-standing block of the same type as {@code brokenState} connected (through
	 * more of the same type, possibly diagonally) to {@code minedPos}, and breaks that one instead - the
	 * original's own "auto-tunnel toward the end of a vein/log" effect. Replaces
	 * {@code BlockUtils.findBlocks}' own recursive "restart the whole cube scan from the new best match"
	 * approach with a plain bounded breadth-first search - same real search (same-type, same reach,
	 * same position caps), a clearer/cheaper way to run it. */
	private static void tryBurrow(ServerLevel level, BlockPos minedPos, BlockState brokenState, Player harvester, ItemStack tool) {
		if (!isValidBurrowBlock(brokenState)) {
			return;
		}
		int reach = brokenState.is(BlockTags.LOGS) ? 2 : 1;
		BlockPos furthest = findFurthestMatchingBlock(level, minedPos, brokenState.getBlock(), reach);
		if (furthest.equals(minedPos)) {
			return;
		}
		burrowingRecursion = true;
		try {
			tool.hurtAndBreak(1, harvester, EquipmentSlot.MAINHAND);
			level.destroyBlock(furthest, true, harvester, 512);
		} finally {
			burrowingRecursion = false;
		}
	}

	private static boolean isValidBurrowBlock(BlockState state) {
		return state.is(BlockTags.LOGS) || state.is(Tags.Blocks.ORES);
	}

	private static BlockPos findFurthestMatchingBlock(ServerLevel level, BlockPos origin, Block match, int reach) {
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		Set<BlockPos> visited = new HashSet<>();
		queue.add(origin);
		visited.add(origin);
		BlockPos furthest = origin;
		long furthestDistSq = 0;

		while (!queue.isEmpty() && visited.size() < BURROW_MAX_VISITED) {
			BlockPos current = queue.poll();
			long dx = current.getX() - origin.getX();
			long dy = current.getY() - origin.getY();
			long dz = current.getZ() - origin.getZ();
			long distSq = dx * dx + dy * dy + dz * dz;
			if (distSq > furthestDistSq) {
				furthestDistSq = distSq;
				furthest = current;
			}
			for (int xx = -reach; xx <= reach; xx++) {
				for (int yy = -reach; yy <= reach; yy++) {
					for (int zz = -reach; zz <= reach; zz++) {
						if (xx == 0 && yy == 0 && zz == 0) {
							continue;
						}
						BlockPos next = current.offset(xx, yy, zz);
						if (visited.contains(next)
								|| Math.abs(next.getX() - origin.getX()) > BURROW_MAX_RADIUS
								|| Math.abs(next.getY() - origin.getY()) > BURROW_MAX_RADIUS
								|| Math.abs(next.getZ() - origin.getZ()) > BURROW_MAX_RADIUS) {
							continue;
						}
						if (level.getBlockState(next).is(match)) {
							visited.add(next);
							queue.add(next);
						}
					}
				}
			}
		}
		return furthest;
	}
}
