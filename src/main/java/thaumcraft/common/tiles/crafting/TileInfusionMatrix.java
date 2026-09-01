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
 * Simplified for this pass (all real features, just not built yet - see the port-tracking memory notes):
 * <ul>
 * <li><b>No wand/caster item exists yet</b>, so the original's wand-right-click activation
 * ({@link IInteractWithCaster#onCasterRightClick}) has no real caller. This tile still implements the
 * interface as a real, working entry point (see {@link #onCasterRightClick}) - it's just that
 * {@link thaumcraft.common.blocks.crafting.BlockInfusionMatrix} calls {@link #activate} directly from a
 * plain empty-hand right-click for now. Once a caster/wand item is ported, wiring it to check for
 * {@code IInteractWithCaster} and call {@code onCasterRightClick} needs no changes here.</li>
 * <li><b>No pillar/accelerator-block tier system.</b> The original required four matching {@code BlockPillar}
 * blocks at the structure's corners for {@code validLocation()} to pass, and Ancient/Eldritch pillar tiers
 * plus {@code matrixSpeed}/{@code matrixCost} blocks modified cycle time and cost. None of those blocks are
 * ported; {@code validLocation()} only requires the central pedestal, and cycle time/cost are fixed
 * baselines ({@link #BASE_CYCLE_TIME}, no cost multiplier).</li>
 * <li><b>No {@code TileStabilizer}/{@code BlockInlay} charge-network "instability mitigator".</b> The
 * original could redirect an instability-event item ejection into a nearby stabilizer block instead of
 * dropping it, if a charge-network path existed. That whole subsystem (candle/skull-adjacent charge
 * propagation) isn't ported; ejection always just drops the item.</li>
 * <li><b>Instability events trimmed to what's reachable.</b> The original rolled one of 24 weighted outcomes
 * including flux-goo placement ({@code BlockFluxGoo}, a custom finite-fluid block, not ported) and
 * Taint/Vis-Exhaust potion effects ({@code PotionFluxTaint}/{@code PotionVisExhaust}, not ported). This
 * keeps item ejection (with an {@link AuraHelper#polluteAura} variant replacing flux-goo placement),
 * living-entity zap damage, and player warp gain (a real, already-ported system) - see
 * {@link #triggerInstabilityEvent}.</li>
 * <li><b>Recipe output is always a plain {@code ItemStack}.</b> {@link InfusionRecipe} was redesigned as a
 * modern data-driven {@code Recipe} during this port and dropped the old dynamic
 * {@code Object}-typed output (NBT tag / enchantment / XP-cost variants) - see that class's own doc comment.
 * {@link #craftingFinish} only needs the plain-ItemStack path as a result.</li>
 * <li>No particle/sound FX beyond a couple of vanilla stand-in sounds - the client FX dispatcher used
 * throughout the original for this tile isn't ported (same boundary as every other device this session).</li>
 * </ul>
 *
 * The symmetry-based stability scan itself ({@link #getSurroundings}) IS kept close to the original: pedestals
 * were always part of that scan (every {@link BlockPedestal} implements {@link IInfusionStabiliserExt}), and
 * vanilla skull blocks are kept as the original's other zero-dependency stabilizer case.
 */
public class TileInfusionMatrix extends BlockEntity implements IInteractWithCaster, IAspectContainer {

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
		if (tile.checkSurroundings) {
			tile.checkSurroundings = false;
			tile.getSurroundings(level);
		}
		if (level.isClientSide) return;

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

	public boolean validLocation(Level level) {
		return level.getBlockEntity(worldPosition.below(2)) instanceof TilePedestal;
	}

	@Override
	public boolean onCasterRightClick(Level level, ItemStack casterStack, Player player, BlockPos pos, Direction side, InteractionHand hand) {
		return activate(level, player);
	}

	/** Toggles the altar on, or (if already active and idle) starts a craft attempt against whatever is
	 * currently on the pedestals. See this class's own doc comment for why this is called directly from a
	 * plain block right-click for now instead of only through {@link #onCasterRightClick}. */
	public boolean activate(Level level, Player player) {
		if (level.isClientSide) return false;
		if (active && !crafting) {
			craftingStart(level, player);
			return true;
		}
		if (!active && validLocation(level)) {
			active = true;
			level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.5f, 1.0f);
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
		recipeEssentia = recipe.getAspects(player, central, components).copy();
		recipeInputSnapshot = central.copy();
		recipePlayer = player.getUUID();
		crafting = true;
		level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.5f, 1.0f);
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
			level.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0f, 0.6f);
			setChanged();
			return;
		}

		if (recipeEssentia.visSize() > 0) {
			for (Aspect aspect : recipeEssentia.getAspects()) {
				int amount = recipeEssentia.getAmount(aspect);
				if (amount <= 0) continue;
				if (EssentiaHandler.drainEssentia(this, aspect, ESSENTIA_RANGE)) {
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
						ped2.setItem(ItemStack.EMPTY);
						recipeIngredients.remove(a);
						setChanged();
						return;
					}
				}
			}
			return;
		}

		crafting = false;
		craftingFinish(level);
		setChanged();
	}

	private void triggerInstabilityEvent(Level level, RandomSource rand) {
		inResAdd(level);
		switch (rand.nextInt(12)) {
			case 0, 1, 2, 3 -> inEvEjectItem(level, rand, false);
			case 4, 5 -> inEvWarp(level, rand);
			case 6, 7 -> inEvZap(level, rand, false);
			case 8 -> inEvZap(level, rand, true);
			case 9, 10 -> inEvEjectItem(level, rand, true);
			default -> level.explode(null, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, 1.5f + rand.nextFloat(), false, Level.ExplosionInteraction.BLOCK);
		}
	}

	private AABB nearbyBox() {
		return new AABB(worldPosition).inflate(10.0);
	}

	private void inEvZap(Level level, RandomSource rand, boolean all) {
		for (net.minecraft.world.entity.LivingEntity target : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, nearbyBox())) {
			target.hurt(level.damageSources().magic(), 4 + rand.nextInt(4));
			if (!all) break;
		}
	}

	private void inEvWarp(Level level, RandomSource rand) {
		List<Player> targets = level.getEntitiesOfClass(Player.class, nearbyBox());
		if (targets.isEmpty()) return;
		Player target = targets.get(rand.nextInt(targets.size()));
		if (rand.nextFloat() < 0.25f) {
			ThaumcraftCapabilities.getWarp(target).add(IPlayerWarp.EnumWarpType.NORMAL, 1);
		} else {
			ThaumcraftCapabilities.getWarp(target).add(IPlayerWarp.EnumWarpType.TEMPORARY, 2 + rand.nextInt(4));
		}
	}

	private void inEvEjectItem(Level level, RandomSource rand, boolean pollute) {
		if (pedestals.isEmpty()) return;
		for (int tries = 0; tries < 25; tries++) {
			BlockPos pp = pedestals.get(rand.nextInt(pedestals.size()));
			if (level.getBlockEntity(pp) instanceof TilePedestal ped && !ped.getItem().isEmpty()) {
				ItemStack dropped = ped.getItem();
				ped.setItem(ItemStack.EMPTY);
				level.addFreshEntity(new ItemEntity(level, pp.getX() + 0.5, pp.getY() + 1.0, pp.getZ() + 0.5, dropped));
				if (pollute) {
					AuraHelper.polluteAura(level, pp, 5 + rand.nextInt(5), true);
				}
				return;
			}
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
		recipeEssentia = new AspectList();
		recipeInstability = 0;
		recipeOutput = ItemStack.EMPTY;
		level.playSound(null, worldPosition, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.5f, 1.0f);
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
		countDelay = Math.max(1, BASE_CYCLE_TIME / 2);
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
}
