package thaumcraft.common.tiles.crafting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectContainer;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.capabilities.IPlayerWarp;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.casters.IInteractWithCaster;
import thaumcraft.api.crafting.IInfusionStabiliser;
import thaumcraft.api.crafting.IInfusionStabiliserExt;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.api.crafting.InfusionRecipeInput;
import thaumcraft.api.crafting.ThaumcraftRecipeTypes;
import thaumcraft.common.blocks.devices.BlockPedestal;
import thaumcraft.common.lib.events.EssentiaHandler;
import thaumcraft.common.registry.ThaumcraftBlockEntities;

/**
 * Replaces the old (1.12.2) {@code TileInfusionMatrix} - the "Runic Matrix" block that sits atop an infusion
 * altar. Same core loop: a central {@link TilePedestal} two blocks below holds the item being infused,
 * surrounding pedestals hold the components, and once activated the altar slowly drains essentia from
 * nearby sources (via {@link EssentiaHandler}) and consumes the component items to craft the
 * {@link InfusionRecipe}'s result - with a chance of an "instability event" each cycle if the structure is
 * imperfect or essentia is scarce.
 *
 * As the original:
 * <ul>
 * <li>Activated only with a caster ({@link #onCasterRightClick}).</li>
 * <li>Four {@link thaumcraft.common.blocks.basic.BlockPillar}s at the corners; a uniform set of ancient or eldritch
 * pillars, and speed/cost matrices at the outer corners, change cycle time, cost and stability
 * ({@link #getSurroundings}).</li>
 * <li>Components take 5 cycles each to draw off their pedestals (the stream starts on the first) and leave their
 * container item behind; a component missing from every pedestal sometimes costs an extra essentia and some
 * stability.</li>
 * <li>The full 24-weighted instability table ({@link #triggerInstabilityEvent}): item ejection in its six variants
 * (which an energised {@link thaumcraft.common.tiles.devices.TileStabilizer} can swallow), zaps, warp, and flux
 * taint or milk-proof vis exhaustion ({@link #inEvHarm}).</li>
 * <li>Recipes may carry data components on their results (the elemental tools come out pre-infused).</li>
 * <li>The original's sounds and client effects ({@link #doEffects}): the infuser hum, streams from the pedestals,
 * sparks while unstable and over the blocks spoiling the symmetry, and the pedestal puff/shield block events.</li>
 * </ul>
 *
 * The symmetry-based stability scan itself ({@link #getSurroundings}) IS kept close to the original: pedestals
 * were always part of that scan (every {@link BlockPedestal} implements {@link IInfusionStabiliserExt}), and
 * vanilla skull blocks are kept as the original's other zero-dependency stabilizer case.
 *
 * {@link #setChanged()} is overridden below - see {@link thaumcraft.common.tiles.crafting.TilePedestal#setChanged()}'s
 * own doc comment for why plain {@code setChanged()} doesn't push live client updates on its own; needed here
 * for {@link thaumcraft.client.render.TileInfusionMatrixRenderer}.
 */
public class TileInfusionMatrix extends BlockEntity implements IInteractWithCaster, IAspectContainer, thaumcraft.api.items.IGogglesDisplayExtended {

	private static final int STABILITY_CAP = 25;
	private static final int BASE_CYCLE_TIME = 10;
	private static final int ESSENTIA_RANGE = 12;

	private final List<BlockPos> pedestals = new ArrayList<>();
	private final List<BlockPos> problemBlocks = new ArrayList<>();
	private final Map<Block, Integer> stabilizerCounts = new HashMap<>();

	public boolean active;
	public boolean crafting;
	private boolean checkSurroundings = true;
	public float stability;
	public float stabilityReplenish;
	private int countDelay = BASE_CYCLE_TIME / 2;
	private int count;
	/** Real cycle-time/cost accumulators, reset to their real base values ({@link #BASE_CYCLE_TIME}/1.0) at
	 * the start of every {@link #getSurroundings} scan and then modified by real pillar/matrixSpeed/matrixCost/
	 * pedestal-tier bonuses - see that method for the exact real formula. */
	private int cycleTime = BASE_CYCLE_TIME;
	private float costMult = 1.0f;
	/** Real, purely-derived animation state for {@link thaumcraft.client.render.TileInfusionMatrixRenderer} -
	 * the original's own real {@code startUp}/{@code craftcount} fields. Deliberately NOT synced or persisted:
	 * {@link #tick} runs identically on both logical sides (like {@link #count} above), so each side recomputes
	 * its own value from the already-real-synced {@link #active}/{@link #crafting} booleans rather than the
	 * server pushing a third pair of fields across the network - same "derive it locally" shape
	 * {@code FocusRadialOverlay}'s own client-side animation state already uses, just living on the tile so
	 * both the renderer and any future consumer share one real definition. {@link #activatedTick} counts up
	 * while {@link #active} (a real 0-1 startup ease-in ramp), resetting the instant it isn't; {@link #craftTick}
	 * does the same for {@link #crafting} (a real ramping halo-intensity signal). */
	private int activatedTick;
	private int craftTick;
	/** The original's itemCount: a component takes 5 cycles to draw off its pedestal (the stream starts on the first). */
	private int itemCount;
	/** Client only: the original's craftCount (infuser sound loop) and sourceFX (streams into the matrix), keyed by
	 * "x:y:z:color" as the original's packet did. */
	private int craftCount;
	public final java.util.Map<String, SourceFX> sourceFX = new java.util.HashMap<>();

	/** A stream feeding the matrix: from a pedestal's item (60 ticks), or from the entity {@code color} names when
	 * {@code loc} is the matrix itself (15 ticks). */
	public static final class SourceFX {
		public final BlockPos loc;
		public int ticks;
		public final int color;

		public SourceFX(BlockPos loc, int ticks, int color) {
			this.loc = loc;
			this.ticks = ticks;
			this.color = color;
		}
	}

	private AspectList recipeEssentia = new AspectList();
	private List<ItemStack> recipeIngredients = new ArrayList<>();
	private ItemStack recipeOutput = ItemStack.EMPTY;
	private ItemStack recipeInputSnapshot = ItemStack.EMPTY;
	private int recipeInstability;
	private UUID recipePlayer;

	public TileInfusionMatrix(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.INFUSION_MATRIX.get(), pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TileInfusionMatrix tile) {
		tile.count++;
		tile.activatedTick = tile.active ? tile.activatedTick + 1 : 0;
		tile.craftTick = tile.crafting ? tile.craftTick + 1 : 0;
		if (tile.checkSurroundings) {
			tile.checkSurroundings = false;
			tile.getSurroundings(level);
		}
		// Real client-only crafting rune restored 2026-09-17 (was dropped under this port's old blanket "no
		// client FX" rule) - routed through ClientFxHandlers#spawnBlockRunes for the same real
		// dist-crash-avoidance reason every other client-only trigger in this port uses.
		if (level.isClientSide) {
			tile.doEffects(level, pos);
			return;
		}

		if (tile.count % (tile.crafting ? 20 : 100) == 0 && !tile.validLocation(level)) {
			tile.active = false;
			tile.setChanged();
			return;
		}
		if (tile.active && !tile.crafting && tile.stability < STABILITY_CAP && tile.count % Math.max(5, tile.countDelay) == 0) {
			tile.stability = Math.min(STABILITY_CAP, tile.stability + Math.max(0.1f, tile.stabilityReplenish));
			tile.setChanged();
		}
		if (tile.active && tile.crafting && tile.countDelay > 0 && tile.count % tile.countDelay == 0) {
			tile.craftCycle(level);
		}
	}

	/** The original's client-side doEffects: the infuser sound (a start, then every 65 ticks), the crafting runes, the
	 * streams from pedestals into the matrix, sparks while unstable, and sparks over the blocks spoiling the symmetry. */
	private void doEffects(Level level, BlockPos pos) {
		RandomSource rand = level.random;
		if (crafting) {
			if (craftCount == 0) {
				level.playLocalSound(pos.getX(), pos.getY(), pos.getZ(), thaumcraft.common.registry.ThaumcraftSounds.INFUSERSTART.get(),
						SoundSource.BLOCKS, 0.5f, 1.0f, false);
			} else if (craftCount % 65 == 0) {
				level.playLocalSound(pos.getX(), pos.getY(), pos.getZ(), thaumcraft.common.registry.ThaumcraftSounds.INFUSER.get(),
						SoundSource.BLOCKS, 0.5f, 1.0f, false);
			}
			craftCount++;
			thaumcraft.client.fx.ClientFxHandlers.spawnBlockRunes(level, pos.getX(), pos.getY() - 2, pos.getZ(),
					0.5f + rand.nextFloat() * 0.2f, 0.1f, 0.7f + rand.nextFloat() * 0.3f, 25, -0.03f);
		} else if (craftCount > 0) {
			craftCount = Math.min(50, Math.max(0, craftCount - 2));
		}
		for (String key : sourceFX.keySet().toArray(new String[0])) {
			SourceFX fx = sourceFX.get(key);
			if (fx.ticks <= 0) {
				sourceFX.remove(key);
				continue;
			}
			if (fx.loc.equals(pos)) {
				net.minecraft.world.entity.Entity source = level.getEntity(fx.color);
				if (source != null) {
					for (int a = 0; a < 4; a++) {
						thaumcraft.client.fx.ClientFxHandlers.spawnInfusionStream(level,
								source.getX() + (rand.nextFloat() - rand.nextFloat()) * source.getBbWidth(),
								source.getBoundingBox().minY + rand.nextFloat() * source.getBbHeight(),
								source.getZ() + (rand.nextFloat() - rand.nextFloat()) * source.getBbWidth(), pos, ItemStack.EMPTY, true);
					}
				}
			} else if (level.getBlockEntity(fx.loc) instanceof TilePedestal pedestal) {
				ItemStack is = pedestal.getItem();
				if (!is.isEmpty()) {
					thaumcraft.client.fx.ClientFxHandlers.spawnInfusionStream(level, fx.loc.getX(), fx.loc.getY(), fx.loc.getZ(), pos, is, false);
				}
			} else {
				fx.ticks = 0;
			}
			fx.ticks--;
		}
		if (crafting && stability < 0.0f && rand.nextInt(250) <= Math.abs(stability)) {
			thaumcraft.client.fx.ClientFxHandlers.spawnSpark(level, pos.getX() + rand.nextFloat(), pos.getY() + rand.nextFloat(), pos.getZ() + rand.nextFloat(),
					3.0f + rand.nextFloat() * 2.0f, 0.7f + rand.nextFloat() * 0.1f, 0.1f, 0.65f + rand.nextFloat() * 0.1f, 0.8f);
		}
		if (active && !problemBlocks.isEmpty() && rand.nextInt(25) == 0) {
			BlockPos p = problemBlocks.get(rand.nextInt(problemBlocks.size()));
			thaumcraft.client.fx.ClientFxHandlers.spawnSpark(level, p.getX() + rand.nextFloat(), p.getY() + rand.nextFloat(), p.getZ() + rand.nextFloat(),
					2.0f + rand.nextFloat(), 0.7f + rand.nextFloat() * 0.1f, 0.1f, 0.65f + rand.nextFloat() * 0.1f, 0.8f);
		}
	}

	/** The 4 real corner offsets (2 blocks below, diagonal) a real {@code BlockPillar} must occupy - same real
	 * position the true original's own {@code validLocation}/{@code getSurroundings} both use. */
	private static final int[] PILLAR_X = { -1, 1, 1, -1 };
	private static final int[] PILLAR_Z = { -1, -1, 1, 1 };

	/** Real accessor for {@link #costMult} - the real per-corner pillar/matrixSpeed/matrixCost/pedestal-tier
	 * cost multiplier {@link #getSurroundings} builds up each scan (see that method's own doc comment for the
	 * exact real formula). Exists purely for real test observability, matching this project's own established
	 * "add a minimal public getter rather than reach into a private field" precedent elsewhere. */
	public float getCostMult() {
		return costMult;
	}

	public boolean validLocation(Level level) {
		if (!(level.getBlockEntity(worldPosition.below(2)) instanceof TilePedestal)) return false;
		for (int i = 0; i < 4; i++) {
			if (!(level.getBlockState(worldPosition.offset(PILLAR_X[i], -2, PILLAR_Z[i])).getBlock()
					instanceof thaumcraft.common.blocks.basic.BlockPillar)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean onCasterRightClick(Level level, ItemStack casterStack, Player player, BlockPos pos, Direction side, InteractionHand hand) {
		// as the original: the client rescans its surroundings (for the problem-block sparks) when an idle altar is poked
		if (level.isClientSide && active && !crafting) checkSurroundings = true;
		return activate(level, player);
	}

	/** Toggles the altar on, or (if already active and idle) starts a craft attempt against whatever is
	 * currently on the pedestals. Reached through {@link #onCasterRightClick}. */
	public boolean activate(Level level, Player player) {
		if (level.isClientSide) return false;
		if (active && !crafting) {
			craftingStart(level, player);
			return true;
		}
		if (!active && validLocation(level)) {
			active = true;
			level.playSound(null, worldPosition, thaumcraft.common.registry.ThaumcraftSounds.CRAFTSTART.get(), SoundSource.BLOCKS, 0.5f, 1.0f);
			setChanged();
			return true;
		}
		return false;
	}

	private void craftingStart(Level level, Player player) {
		if (!validLocation(level)) {
			active = false;
			setChanged();
			return;
		}
		getSurroundings(level);

		ItemStack central = level.getBlockEntity(worldPosition.below(2)) instanceof TilePedestal ped ? ped.getItem() : ItemStack.EMPTY;
		if (central.isEmpty()) return;

		List<ItemStack> components = new ArrayList<>();
		for (BlockPos pp : pedestals) {
			if (level.getBlockEntity(pp) instanceof TilePedestal ped && !ped.getItem().isEmpty()) {
				components.add(ped.getItem().copy());
			}
		}
		if (components.isEmpty()) return;

		InfusionRecipeInput input = new InfusionRecipeInput(central.copy(), components);
		Optional<RecipeHolder<InfusionRecipe>> match = level.getRecipeManager().getRecipeFor(ThaumcraftRecipeTypes.INFUSION_TYPE.get(), input, level);
		if (match.isEmpty()) return;
		InfusionRecipe recipe = match.get().value();

		if (!recipe.getResearch().isEmpty() && !player.getAbilities().instabuild
				&& !ThaumcraftCapabilities.getKnowledge(player).isResearchKnown(recipe.getResearch())) {
			return;
		}

		recipeIngredients = components;
		recipeOutput = recipe.getRecipeOutput(player, central, components).copy();
		recipeInstability = recipe.getInstability(player, central, components);
		// Real costMult scaling (see getSurroundings for how it's built up) - clamped to a real minimum of
		// 0.5 (can't go below half real cost), same real formula as the true original's own craftingStart.
		if (costMult < 0.5f) costMult = 0.5f;
		AspectList baseEssentia = recipe.getAspects(player, central, components);
		AspectList scaledEssentia = new AspectList();
		for (Aspect aspect : baseEssentia.getAspects()) {
			int scaled = (int) (baseEssentia.getAmount(aspect) * costMult);
			if (scaled > 0) scaledEssentia.add(aspect, scaled);
		}
		recipeEssentia = scaledEssentia;
		recipeInputSnapshot = central.copy();
		recipePlayer = player.getUUID();
		crafting = true;
		itemCount = 0;
		level.playSound(null, worldPosition, thaumcraft.common.registry.ThaumcraftSounds.CRAFTSTART.get(), SoundSource.BLOCKS, 0.5f, 1.0f);
		setChanged();
	}

	private float getLossPerCycle() {
		float mod = switch (stabilityState()) {
			case VERY_STABLE -> 5.0f;
			case STABLE -> 6.0f;
			case UNSTABLE -> 7.0f;
			case VERY_UNSTABLE -> 8.0f;
		};
		return recipeInstability / mod;
	}

	private enum Stability { VERY_STABLE, STABLE, UNSTABLE, VERY_UNSTABLE }

	private static final java.text.DecimalFormat GOGGLES_FORMAT = new java.text.DecimalFormat("#######.##");

	/** The original's goggles readout: the stability state, what the surroundings give back each cycle, and - while a
	 * recipe's instability is in play - the most a cycle can take. Client side (synced fields). */
	@Override
	public String[] getIGogglesText() {
		String state = net.minecraft.ChatFormatting.BOLD + net.minecraft.network.chat.Component.translatable("stability." + stabilityState().name()).getString();
		String gain = net.minecraft.ChatFormatting.GOLD + "" + net.minecraft.ChatFormatting.ITALIC + GOGGLES_FORMAT.format(stabilityReplenish) + " "
				+ net.minecraft.network.chat.Component.translatable("stability.gain").getString();
		float lpc = getLossPerCycle();
		if (lpc != 0.0f) {
			return new String[] { state, gain, net.minecraft.ChatFormatting.RED + net.minecraft.network.chat.Component.translatable("stability.range").getString()
					+ net.minecraft.ChatFormatting.ITALIC + GOGGLES_FORMAT.format(lpc) + " " + net.minecraft.network.chat.Component.translatable("stability.loss").getString() };
		}
		return new String[] { state, gain };
	}

	private Stability stabilityState() {
		if (stability > STABILITY_CAP / 2f) return Stability.VERY_STABLE;
		if (stability >= 0f) return Stability.STABLE;
		if (stability > -25f) return Stability.UNSTABLE;
		return Stability.VERY_UNSTABLE;
	}

	private void craftCycle(Level level) {
		RandomSource rand = level.getRandom();
		stability -= rand.nextFloat() * getLossPerCycle();
		stability += stabilityReplenish;
		stability = Mth.clamp(stability, -100f, STABILITY_CAP);

		boolean valid = level.getBlockEntity(worldPosition.below(2)) instanceof TilePedestal ped
				&& !ped.getItem().isEmpty()
				&& ItemStack.isSameItemSameComponents(ped.getItem(), recipeInputSnapshot);

		if (!valid || (stability < 0f && rand.nextInt(1500) <= Math.abs(stability))) {
			triggerInstabilityEvent(level, rand);
			stability += 5.0f + rand.nextFloat() * 5.0f;
			setChanged();
			if (valid) return;
		}
		if (!valid) {
			crafting = false;
			recipeEssentia = new AspectList();
			recipeInstability = 0;
			level.playSound(null, worldPosition, thaumcraft.common.registry.ThaumcraftSounds.CRAFTFAIL.get(), SoundSource.BLOCKS, 1.0f, 0.6f);
			setChanged();
			return;
		}

		if (recipeEssentia.visSize() > 0) {
			for (Aspect aspect : recipeEssentia.getAspects()) {
				int amount = recipeEssentia.getAmount(aspect);
				if (amount <= 0) continue;
				if (EssentiaHandler.drainEssentia(this, aspect, null, ESSENTIA_RANGE, amount > 1 ? countDelay : 0)) {
					recipeEssentia.reduce(aspect, 1);
					setChanged();
					return;
				}
				stability -= 0.25f;
				setChanged();
			}
			checkSurroundings = true;
			return;
		}

		if (!recipeIngredients.isEmpty()) {
			for (int a = 0; a < recipeIngredients.size(); a++) {
				for (BlockPos pp : pedestals) {
					if (level.getBlockEntity(pp) instanceof TilePedestal ped2
							&& !ped2.getItem().isEmpty()
							&& ItemStack.isSameItemSameComponents(ped2.getItem(), recipeIngredients.get(a))) {
						// as the original: the stream starts, and 5 cycles later the item is gone (leaving its container)
						if (itemCount == 0) {
							itemCount = 5;
							broadcastInfusionSourceFx(level, pp, ped2.getItem().copy());
						} else if (itemCount-- <= 1) {
							ItemStack remainder = ped2.getItem().getCraftingRemainingItem();
							ped2.setItem(remainder.isEmpty() ? ItemStack.EMPTY : remainder.copy());
							recipeIngredients.remove(a);
						}
						setChanged();
						return;
					}
				}
				// as the original: a component missing from every pedestal sometimes costs an extra essentia and some stability
				Aspect[] ingEss = recipeEssentia.getAspects();
				if (ingEss != null && ingEss.length > 0 && rand.nextInt(1 + a) == 0) {
					recipeEssentia.add(ingEss[rand.nextInt(ingEss.length)], 1);
					stability -= 0.25f;
					setChanged();
				}
			}
			return;
		}

		crafting = false;
		craftingFinish(level);
		setChanged();
	}

	/** Starts a component's stream into the matrix on the clients (PacketFXInfusionSource, 60 ticks from a pedestal). */
	private void broadcastInfusionSourceFx(Level level, BlockPos pedestal, ItemStack consumed) {
		if (!(level instanceof ServerLevel serverLevel)) return;
		try {
			PacketDistributor.sendToPlayersNear(serverLevel, null, pedestal.getX(), pedestal.getY(), pedestal.getZ(), 32.0,
					new thaumcraft.common.lib.network.fx.PacketFXInfusionSource(pedestal, worldPosition, consumed));
		} catch (Exception e) {
			Thaumcraft.LOGGER.debug("Infusion source FX packet failed to send (no real client connection?)", e);
		}
	}

	/** Public (not just an implementation detail) specifically so real GameTest coverage can drive this
	 * directly with a controlled {@link RandomSource} - real end-to-end craft-cycle instability is genuinely
	 * probabilistic and slow to exercise every one of the real 24 weighted outcomes through, the same real
	 * "loosen visibility for real testability" precedent {@link
	 * thaumcraft.common.entities.monster.tainted.EntityTaintacle#isAdjacentToSolidBlock} already established
	 * this session. */
	public void triggerInstabilityEvent(Level level, RandomSource rand) {
		inResAdd(level);
		switch (rand.nextInt(24)) {
			case 0, 1, 2, 3 -> inEvEjectItem(level, rand, 0);
			case 4, 5, 6 -> inEvWarp(level, rand);
			case 7, 8, 9 -> inEvZap(level, rand, false);
			case 10, 11 -> inEvZap(level, rand, true);
			case 12, 13 -> inEvEjectItem(level, rand, 1);
			case 14, 15 -> inEvEjectItem(level, rand, 2);
			case 16 -> inEvEjectItem(level, rand, 3);
			case 17 -> inEvEjectItem(level, rand, 4);
			case 18, 19 -> inEvHarm(level, rand, false);
			case 20, 21 -> inEvEjectItem(level, rand, 5);
			case 22 -> inEvHarm(level, rand, true);
			default -> level.explode(null, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, 1.5f + rand.nextFloat(), false, Level.ExplosionInteraction.BLOCK);
		}
	}

	private AABB nearbyBox() {
		return new AABB(worldPosition).inflate(10.0);
	}

	/** Real {@code PacketFXBlockArc} feedback restored 2026-09-15 (see {@link thaumcraft.client.fx.beams.FXArc}'s
	 * own doc comment) - was never self-documented as a gap anywhere in this class before, only found by
	 * cross-checking the true original directly. */
	private void inEvZap(Level level, RandomSource rand, boolean all) {
		net.minecraft.server.level.ServerLevel serverLevel = level instanceof net.minecraft.server.level.ServerLevel sl ? sl : null;
		for (net.minecraft.world.entity.LivingEntity target : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, nearbyBox())) {
			if (serverLevel != null) {
				try {
					net.neoforged.neoforge.network.PacketDistributor.sendToPlayersNear(serverLevel, null,
							worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), 32.0,
							thaumcraft.common.lib.network.fx.PacketFXBlockArc.of(worldPosition, target,
									0.3f - rand.nextFloat() * 0.1f, 0.0f, 0.3f - rand.nextFloat() * 0.1f));
				} catch (Exception e) {
					thaumcraft.Thaumcraft.LOGGER.debug("Infusion Matrix zap FX packet failed to send (no real client connection?)", e);
				}
			}
			target.hurt(level.damageSources().magic(), 4 + rand.nextInt(4));
			if (!all) break;
		}
	}

	private void inEvWarp(Level level, RandomSource rand) {
		List<Player> targets = level.getEntitiesOfClass(Player.class, nearbyBox());
		if (targets.isEmpty()) return;
		Player target = targets.get(rand.nextInt(targets.size()));
		if (rand.nextFloat() < 0.25f) {
			thaumcraft.api.ThaumcraftApi.internalMethods.addWarpToPlayer(target, 1, IPlayerWarp.EnumWarpType.NORMAL);
		} else {
			thaumcraft.api.ThaumcraftApi.internalMethods.addWarpToPlayer(target, 2 + rand.nextInt(4), IPlayerWarp.EnumWarpType.TEMPORARY);
		}
	}

	/** Real port of the true original's own {@code inEvEjectItem(int type)} - see this class's own doc
	 * comment for the real weighting each type is called at. Real {@code TileStabilizer} mitigation check,
	 * restored 2026-09-15 (see that class's own doc comment): if the matched pedestal is reachable from a
	 * real, energized stabilizer (via {@link TilePedestal#findInstabilityMitigator}), the ejection is
	 * absorbed there instead (a real 5-10 energy cost, matching the true original's own
	 * {@code MathHelper.getInt(rand, 5, 10)} exactly) and nothing else about this type's own real effect
	 * happens at all - matches the true original's own early-return exactly. Otherwise: types 0/1/2/3/5
	 * really drop the ejected item as a real {@link ItemEntity}; type 4 clears the pedestal without dropping
	 * anything (matches the true original's own {@code type <= 3 || type == 5} split exactly). Types 1/3
	 * additionally place a real {@link thaumcraft.api.blocks.BlocksTC#fluxGoo} above the pedestal; types 2/4
	 * additionally pollute the real local aura; type 5 additionally sets off a real explosion at the
	 * pedestal itself (independent of the plain matrix-centered explosion
	 * {@link #triggerInstabilityEvent}'s own default case already covers). Real {@code PacketFXBlockArc}
	 * feedback restored 2026-09-15 - see {@link #sendArcPulse}. */
	private void inEvEjectItem(Level level, RandomSource rand, int type) {
		if (pedestals.isEmpty()) return;
		if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
		for (int tries = 0; tries < 25; tries++) {
			BlockPos pp = pedestals.get(rand.nextInt(pedestals.size()));
			if (level.getBlockEntity(pp) instanceof TilePedestal ped && !ped.getItem().isEmpty()) {
				BlockPos stabPos = ped.findInstabilityMitigator();
				if (stabPos != null && level.getBlockEntity(stabPos) instanceof thaumcraft.common.tiles.devices.TileStabilizer stabilizer
						&& stabilizer.mitigate(5 + rand.nextInt(6))) {
					level.blockEvent(pp, level.getBlockState(pp).getBlock(), 5, 0);
					sendArcPulse(serverLevel, worldPosition, pp.above(), pp, rand);
					sendArcPulse(serverLevel, pp.above(), stabPos, stabPos, rand);
					return;
				}

				ItemStack dropped = ped.getItem();
				ped.setItem(ItemStack.EMPTY);
				if (type <= 3 || type == 5) {
					level.addFreshEntity(new ItemEntity(level, pp.getX() + 0.5, pp.getY() + 1.0, pp.getZ() + 0.5, dropped));
				}
				if (type == 1 || type == 3) {
					level.setBlockAndUpdate(pp.above(), thaumcraft.api.blocks.BlocksTC.fluxGoo.get().defaultBlockState());
					level.playSound(null, pp, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.3f, 1.0f);
				} else if (type == 2 || type == 4) {
					AuraHelper.polluteAura(level, pp, 5 + rand.nextInt(5), true);
				} else if (type == 5) {
					level.explode(null, pp.getX() + 0.5, pp.getY() + 0.5, pp.getZ() + 0.5, 1.0f, false, Level.ExplosionInteraction.BLOCK);
				}
				level.blockEvent(pp, level.getBlockState(pp).getBlock(), 11, 0);
				sendArcPulse(serverLevel, worldPosition, pp.above(), pp, rand);
				return;
			}
		}
	}

	/** Real {@code PacketFXBlockArc} feedback shared by {@link #inEvEjectItem}'s own 2 real call sites
	 * (a plain single pulse for the normal eject outcomes, 2 chained pulses - matrix to pedestal, pedestal to
	 * stabilizer - for the real stabilizer-redirect case) - restored 2026-09-15, see
	 * {@link thaumcraft.client.fx.beams.FXArc}'s own doc comment. Never self-documented as a gap here before,
	 * only found by cross-checking the true original directly. */
	private void sendArcPulse(net.minecraft.server.level.ServerLevel serverLevel, BlockPos from, BlockPos to, BlockPos broadcastCenter, RandomSource rand) {
		try {
			net.neoforged.neoforge.network.PacketDistributor.sendToPlayersNear(serverLevel, null,
					broadcastCenter.getX(), broadcastCenter.getY(), broadcastCenter.getZ(), 32.0,
					thaumcraft.common.lib.network.fx.PacketFXBlockArc.of(from, to,
							0.3f - rand.nextFloat() * 0.1f, 0.0f, 0.3f - rand.nextFloat() * 0.1f));
		} catch (Exception e) {
			thaumcraft.Thaumcraft.LOGGER.debug("Infusion Matrix eject arc FX packet failed to send (no real client connection?)", e);
		}
	}

	/** The original's {@code inEvHarm}: a coin flip between a short Flux Taint and a long, milk-proof Vis Exhaustion, for
	 * one nearby creature or (with {@code all}) every one. */
	private void inEvHarm(Level level, RandomSource rand, boolean all) {
		for (net.minecraft.world.entity.LivingEntity target : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, nearbyBox())) {
			if (rand.nextBoolean()) {
				target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
						thaumcraft.common.registry.ThaumcraftMobEffects.FLUX_TAINT, 120, 0, false, true));
			} else {
				target.addEffect(thaumcraft.common.registry.ThaumcraftMobEffects.milkProof(new net.minecraft.world.effect.MobEffectInstance(
						thaumcraft.common.registry.ThaumcraftMobEffects.VIS_EXHAUST, 2400, 0, true, true)));
			}
			if (!all) break;
		}
	}

	private void inResAdd(Level level) {
		for (Player target : level.getEntitiesOfClass(Player.class, nearbyBox())) {
			IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(target);
			if (!knowledge.isResearchKnown("!INSTABILITY")) {
				knowledge.addResearch("!INSTABILITY");
				target.displayClientMessage(Component.translatable("got.instability"), true);
			}
		}
	}

	private void craftingFinish(Level level) {
		if (!(level.getBlockEntity(worldPosition.below(2)) instanceof TilePedestal ped)) return;
		ItemStack central = ped.getItem();
		ItemStack out = recipeOutput.copy();
		if (!central.isEmpty() && central.isDamageableItem() && central.isDamaged() && out.isDamageableItem()) {
			float ratio = central.getDamageValue() / (float) central.getMaxDamage();
			out.setDamageValue((int) (out.getMaxDamage() * ratio));
		}
		ped.setItem(out);
		// as the original: the player who started the craft crafted it (research asking for an infusion product, warp)
		if (recipePlayer != null && level.getPlayerByUUID(recipePlayer) instanceof Player crafter) {
			net.neoforged.neoforge.event.EventHooks.firePlayerCraftingEvent(crafter, out.copy(), new net.minecraft.world.SimpleContainer(1));
		}
		recipeEssentia = new AspectList();
		recipeInstability = 0;
		recipeOutput = ItemStack.EMPTY;
		// the original: a puff off the central pedestal, and the wand sound
		level.blockEvent(worldPosition.below(2), level.getBlockState(worldPosition.below(2)).getBlock(), 12, 0);
		level.playSound(null, worldPosition, thaumcraft.common.registry.ThaumcraftSounds.WAND.get(), SoundSource.BLOCKS, 0.5f, 1.0f);
		setChanged();
	}

	/**
	 * The 17x17x11 structure/symmetry scan. Finds all pedestals in range (the altar's actual component
	 * slots) and, separately, all {@link IInfusionStabiliser} blocks (pedestals included) for the
	 * symmetry-based stability bonus/penalty - matching pairs on opposite sides of the altar contribute
	 * {@code stabilityReplenish}, mismatched ones penalize it. See this class's own doc comment for what's
	 * cut from the original version of this scan.
	 */
	private void getSurroundings(Level level) {
		pedestals.clear();
		problemBlocks.clear();
		stabilizerCounts.clear();
		stabilityReplenish = 0.0f;
		cycleTime = BASE_CYCLE_TIME;
		costMult = 1.0f;

		Set<Long> stuff = new HashSet<>();
		for (int xx = -8; xx <= 8; xx++) {
			for (int zz = -8; zz <= 8; zz++) {
				if (xx == 0 && zz == 0) continue;
				for (int yy = -3; yy <= 7; yy++) {
					BlockPos p = worldPosition.offset(xx, -yy, zz);
					BlockState state = level.getBlockState(p);
					Block block = state.getBlock();
					if (block instanceof BlockPedestal) {
						pedestals.add(p.immutable());
					}
					boolean stabilizer = block instanceof SkullBlock
							|| (block instanceof IInfusionStabiliser stab && stab.canStabaliseInfusion(level, p));
					if (stabilizer) {
						stuff.add(p.asLong());
					}
				}
			}
		}

		while (!stuff.isEmpty()) {
			long lp = stuff.iterator().next();
			stuff.remove(lp);
			BlockPos c1 = BlockPos.of(lp);
			int dx = worldPosition.getX() - c1.getX();
			int dz = worldPosition.getZ() - c1.getZ();
			BlockPos c2 = new BlockPos(worldPosition.getX() + dx, c1.getY(), worldPosition.getZ() + dz);
			stuff.remove(c2.asLong());

			Block b1 = level.getBlockState(c1).getBlock();
			Block b2 = level.getBlockState(c2).getBlock();
			float amt1 = (b1 instanceof IInfusionStabiliserExt ext1) ? ext1.getStabilizationAmount(level, c1) : 0.1f;
			float amt2 = (b2 instanceof IInfusionStabiliserExt ext2) ? ext2.getStabilizationAmount(level, c2) : 0.1f;

			if (b1 == b2 && amt1 == amt2) {
				if (b1 instanceof IInfusionStabiliserExt ext && ext.hasSymmetryPenalty(level, c1, c2)) {
					stabilityReplenish -= ext.getSymmetryPenalty(level, c1);
					problemBlocks.add(c1);
				} else {
					stabilityReplenish += diminishingReturns(b1, amt1);
				}
			} else {
				stabilityReplenish -= Math.max(amt1, amt2);
				problemBlocks.add(c1);
			}
		}

		// Real pillar-tier bonus: a UNIFORM set of all 4 corners being pillarAncient (or all 4 pillarEldritch)
		// gives a real cycle-time/cost/stability bonus, on top of validLocation's own real "any BlockPillar"
		// structural requirement - same real positions/formula as the true original.
		boolean allAncient = true, allEldritch = true;
		for (int i = 0; i < 4; i++) {
			Block corner = level.getBlockState(worldPosition.offset(PILLAR_X[i], -2, PILLAR_Z[i])).getBlock();
			if (corner != thaumcraft.api.blocks.BlocksTC.pillarAncient.get()) allAncient = false;
			if (corner != thaumcraft.api.blocks.BlocksTC.pillarEldritch.get()) allEldritch = false;
		}
		if (allAncient) {
			cycleTime -= 1;
			costMult -= 0.1f;
			stabilityReplenish -= 0.1f;
		}
		if (allEldritch) {
			cycleTime -= 3;
			costMult += 0.05f;
			stabilityReplenish += 0.2f;
		}

		// Real matrixSpeed/matrixCost corner accelerators - one tier further down/out than the pillars,
		// checked independently per corner (unlike the pillar bonus above, these don't need a matching set).
		for (int i = 0; i < 4; i++) {
			Block corner = level.getBlockState(worldPosition.offset(PILLAR_X[i], -3, PILLAR_Z[i])).getBlock();
			if (corner == thaumcraft.api.blocks.BlocksTC.matrixSpeed.get()) {
				cycleTime -= 1;
				costMult += 0.01f;
			}
			if (corner == thaumcraft.api.blocks.BlocksTC.matrixCost.get()) {
				cycleTime += 1;
				costMult -= 0.02f;
			}
		}

		// Real pedestalEldritch/pedestalAncient cost bonuses, matching the true original's own real, opposite
		// signs exactly - Eldritch raises cost slightly, Ancient lowers it. Both tiers' own stone/pedestal
		// chain is real but genuinely unreachable in survival (stoneAncient/stoneEldritchTile have zero
		// acquisition path anywhere in the true original itself, not a porting gap - see BlocksTC.stoneAncient's
		// own doc comment), so neither check can actually fire outside a creative-mode/GameTest setup, same as
		// the true original's own code.
		for (BlockPos pp : pedestals) {
			Block pedestalBlock = level.getBlockState(pp).getBlock();
			if (pedestalBlock == thaumcraft.api.blocks.BlocksTC.pedestalEldritch.get()) {
				costMult += 0.0025f;
			}
			if (pedestalBlock == thaumcraft.api.blocks.BlocksTC.pedestalAncient.get()) {
				costMult -= 0.01f;
			}
		}

		countDelay = Math.max(1, cycleTime / 2);
	}

	private float diminishingReturns(Block b, float base) {
		int c = stabilizerCounts.getOrDefault(b, 0);
		float result = (c > 0) ? (float) (base * Math.pow(0.75, c)) : base;
		stabilizerCounts.put(b, c + 1);
		return result;
	}

	@Override
	public AspectList getAspects() {
		return recipeEssentia;
	}

	/** How many real component ingredients are still waiting to be pulled off a pedestal - real, useful for
	 * a future progress readout, not just debugging. */
	public int getRemainingIngredientCount() {
		return recipeIngredients.size();
	}

	/** Real ticks since {@link #active} last became true (0 while inactive) - see this field's own doc
	 * comment on why it's derived locally instead of synced. */
	public int getActivatedTick() {
		return activatedTick;
	}

	/** Real ticks since {@link #crafting} last became true (0 while idle) - see this field's own doc comment
	 * on why it's derived locally instead of synced. */
	public int getCraftTick() {
		return craftTick;
	}

	@Override
	public void setAspects(AspectList aspects) {
	}

	@Override
	public int addToContainer(Aspect tag, int amount) {
		return 0;
	}

	@Override
	public boolean takeFromContainer(Aspect tag, int amount) {
		return false;
	}

	@Override
	public boolean takeFromContainer(AspectList ot) {
		return false;
	}

	@Override
	public boolean doesContainerContainAmount(Aspect tag, int amount) {
		return false;
	}

	@Override
	public boolean doesContainerContain(AspectList ot) {
		return false;
	}

	@Override
	public int containerContains(Aspect tag) {
		return 0;
	}

	@Override
	public boolean doesContainerAccept(Aspect tag) {
		return true;
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		active = tag.getBoolean("active");
		crafting = tag.getBoolean("crafting");
		stability = tag.getFloat("stability");
		stabilityReplenish = tag.getFloat("stabilityReplenish");
		recipeInstability = tag.getInt("recipeInstability");
		recipeEssentia = new AspectList();
		recipeEssentia.readFromNBT(tag, "recipeEssentia");
		recipeOutput = tag.contains("recipeOutput") ? ItemStack.parseOptional(registries, tag.getCompound("recipeOutput")) : ItemStack.EMPTY;
		recipeInputSnapshot = tag.contains("recipeInput") ? ItemStack.parseOptional(registries, tag.getCompound("recipeInput")) : ItemStack.EMPTY;
		recipeIngredients = new ArrayList<>();
		if (tag.contains("recipeIngredients")) {
			for (var element : tag.getList("recipeIngredients", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
				recipeIngredients.add(ItemStack.parseOptional(registries, (CompoundTag) element));
			}
		}
		recipePlayer = tag.hasUUID("recipePlayer") ? tag.getUUID("recipePlayer") : null;
		checkSurroundings = true;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putBoolean("active", active);
		tag.putBoolean("crafting", crafting);
		tag.putFloat("stability", stability);
		tag.putFloat("stabilityReplenish", stabilityReplenish);
		tag.putInt("recipeInstability", recipeInstability);
		recipeEssentia.writeToNBT(tag, "recipeEssentia");
		if (!recipeOutput.isEmpty()) tag.put("recipeOutput", recipeOutput.save(registries, new CompoundTag()));
		if (!recipeInputSnapshot.isEmpty()) tag.put("recipeInput", recipeInputSnapshot.save(registries, new CompoundTag()));
		net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
		for (ItemStack stack : recipeIngredients) {
			list.add(stack.save(registries, new CompoundTag()));
		}
		tag.put("recipeIngredients", list);
		if (recipePlayer != null) tag.putUUID("recipePlayer", recipePlayer);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	@Override
	public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
		return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
	}

	/** See {@link thaumcraft.common.tiles.crafting.TilePedestal#setChanged()}'s own doc comment - same real
	 * fix, same reason. Needed here for {@link thaumcraft.client.render.TileInfusionMatrixRenderer} to see
	 * {@link #active}/{@link #crafting}/{@link #stability} change live instead of only after a chunk reload. */
	@Override
	public void setChanged() {
		super.setChanged();
		if (level != null && !level.isClientSide) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
		}
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		thaumcraft.common.lib.events.EssentiaHandler.forget(this);
	}
}
