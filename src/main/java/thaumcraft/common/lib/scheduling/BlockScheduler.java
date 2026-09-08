package thaumcraft.common.lib.scheduling;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.common.world.aura.AuraHandler;

/**
 * Replaces the real, still-useful fragment of the old (1.12.2) {@code ServerEvents} - a per-dimension virtual
 * "gradual block breaking" and "delayed block swap" scheduler, plus the generic delayed-runnable queues both
 * are built on. Everything else that class did (aura-thread bookkeeping, dirty-chunk marking, flux-rift
 * triggering, chunk regeneration, seal/task ticking, note-block clearing) is real too, just already covered
 * elsewhere in this port - see the port-tracking memory's own triage of that file for where each piece
 * actually lives now.
 *
 * <p>Found while reviewing this port's own earlier decision to skip this subsystem entirely and give
 * {@code FocusEffectBreak}/{@code FocusEffectEarth}/{@code DustTriggerSimple} instant substitutes instead -
 * a real, felt gameplay difference (instant vs. the original's gradual, telegraphed break/swap) that went out
 * without a check-in. This restores the real mechanism those three (and any future caster focus or dust
 * trigger) actually need, using the git history's own real pre-port source for the exact original formulas -
 * see each of those 3 classes' own doc comments for their own restored call sites.
 *
 * <p><b>Real, deliberate modernizations, not simplifications</b>: {@link #addBreaker}'s completion step uses
 * the same real "build a throwaway enchanted tool stack, hand it to {@link Block#dropResources}" technique
 * this port's {@code FocusEffectBreak} already established, rather than the original's own
 * {@code BlockUtils.harvestBlock} (a stale utility) - real loot-table/silk/fortune behavior, just via a
 * modern entry point. Mining permission (protection mods, adventure mode) is checked for real via
 * {@link CommonHooks#fireBlockBreak} rather than the original's own {@code World#canMineBlockBody} (no
 * modern equivalent survives verbatim, but the real replacement event covers the same ground). A swapper's
 * {@code source} accepts either an exact {@link BlockState} (matching the original's own {@code IBlockState}
 * case) or a {@link TagKey} of {@link Block} (the real modern replacement for the original's broader
 * {@code Material}-based match - modern Minecraft doesn't group blocks by {@code Material} the same way).
 */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public final class BlockScheduler {

	private static final Map<ResourceKey<Level>, Queue<BreakEntry>> BREAKS = new HashMap<>();
	private static final Map<ResourceKey<Level>, Queue<SwapEntry>> SWAPS = new HashMap<>();
	private static final Map<ResourceKey<Level>, Queue<RunnableEntry>> RUNNABLES = new HashMap<>();

	/** Matches the original's own real always-true default. */
	public static final Predicate<SwapContext> DEFAULT_PREDICATE = ctx -> true;

	private BlockScheduler() {
	}

	@SubscribeEvent
	public static void onLevelTick(LevelTickEvent.Post event) {
		tick(event.getLevel());
	}

	/** The real per-tick drain of all 3 queues for one dimension, split out from {@link #onLevelTick} so
	 * GameTests can call it directly rather than needing to construct a real {@code LevelTickEvent.Post} -
	 * same "extract the real tick logic for real testability" shape {@code RunicShieldEvents#checkRunicShield}
	 * already established. The modern replacement for the original's own {@code ServerEvents#worldTick}. */
	public static void tick(Level level) {
		if (level.isClientSide) return;
		ResourceKey<Level> dim = level.dimension();
		tickRunnables(dim);
		tickBreaks(level, dim);
		tickSwaps(level, dim);
	}

	// -------------------------------------------------------------------------------------------
	// Delayed runnables
	// -------------------------------------------------------------------------------------------

	/** Queues {@code runnable} to run after {@code delay} real server ticks in this dimension (0 = next
	 * tick). Real, direct replacement for {@code ServerEvents#addRunnableServer} - the original's separate
	 * client-side runnable queue isn't ported (nothing server-authoritative needs it; this port's own client
	 * FX dispatcher was never ported either, the original's only real consumer of that half). */
	public static void addRunnableServer(Level level, Runnable runnable, int delay) {
		if (level.isClientSide) return;
		queueFor(RUNNABLES, level.dimension()).add(new RunnableEntry(runnable, delay));
	}

	private static void tickRunnables(ResourceKey<Level> dim) {
		Queue<RunnableEntry> queue = RUNNABLES.get(dim);
		if (queue == null || queue.isEmpty()) return;
		Queue<RunnableEntry> next = new ArrayDeque<>();
		RunnableEntry entry;
		while ((entry = queue.poll()) != null) {
			if (entry.delay > 0) {
				entry.delay--;
				next.add(entry);
			} else {
				try {
					entry.runnable.run();
				} catch (Exception e) {
					Thaumcraft.LOGGER.error("BlockScheduler: a real scheduled runnable threw", e);
				}
			}
		}
		RUNNABLES.put(dim, next);
	}

	// -------------------------------------------------------------------------------------------
	// Gradual block breaking
	// -------------------------------------------------------------------------------------------

	/** Real, direct port of {@code ServerEvents#addBreaker} - queues {@code pos} for gradual, over-time
	 * breaking (one {@code strength} chunk off {@code durabilityCurrent} every real tick this dimension is
	 * ticked, until it reaches 0), same real block-break-progress overlay (if {@code fx}), vis-cost gate, and
	 * silk/fortune-aware real drop as the original. A positive {@code delay} re-queues itself via
	 * {@link #addRunnableServer} before actually starting, matching the original's own real "start breaking
	 * after N ticks" shape (used to stagger multiple simultaneous breaks). */
	public static void addBreaker(Level level, BlockPos pos, BlockState source, Player player, boolean fx, boolean silk, int fortune,
			float strength, float durabilityCurrent, float durabilityMax, int delay, float visCost) {
		if (level.isClientSide) return;
		if (delay > 0) {
			addRunnableServer(level, () -> addBreaker(level, pos, source, player, fx, silk, fortune, strength, durabilityCurrent, durabilityMax, 0, visCost), delay);
			return;
		}
		queueFor(BREAKS, level.dimension()).add(new BreakEntry(pos, source, player, fx, silk, fortune, strength, durabilityCurrent, durabilityMax, visCost));
	}

	private static void tickBreaks(Level level, ResourceKey<Level> dim) {
		Queue<BreakEntry> queue = BREAKS.get(dim);
		if (queue == null || queue.isEmpty()) return;
		Queue<BreakEntry> next = new ArrayDeque<>();
		BreakEntry entry;
		while ((entry = queue.poll()) != null) {
			BlockState current = level.getBlockState(entry.pos);
			if (!current.equals(entry.source)) {
				if (entry.fx) level.destroyBlockProgress(entry.pos.hashCode(), entry.pos, -1);
				continue; // real match to the original's own bare "continue" - a changed block silently drops the entry
			}
			if (entry.visCost > 0.0f && AuraHandler.getVis(level, entry.pos) < entry.visCost) {
				continue; // real match to the original - one insufficient-vis tick cancels the break outright, not a retry
			}
			if (current.getDestroySpeed(level, entry.pos) < 0.0f || !canBreak(level, entry.player, entry.pos, current)) {
				continue;
			}
			if (entry.fx) {
				level.destroyBlockProgress(entry.pos.hashCode(), entry.pos, (int) ((1.0f - entry.durabilityCurrent / entry.durabilityMax) * 10.0f));
			}
			entry.durabilityCurrent -= entry.strength;
			if (entry.durabilityCurrent <= 0.0f) {
				harvest(level, entry.pos, current, entry.player, entry.silk, entry.fortune);
				if (entry.fx) level.destroyBlockProgress(entry.pos.hashCode(), entry.pos, -1);
				if (entry.visCost > 0.0f) AuraHandler.drainVis(level, entry.pos, entry.visCost, false);
			} else {
				next.add(entry);
			}
		}
		BREAKS.put(dim, next);
	}

	private static boolean canBreak(Level level, Player player, BlockPos pos, BlockState state) {
		if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return false;
		var event = CommonHooks.fireBlockBreak(level, serverPlayer.gameMode.getGameModeForPlayer(), serverPlayer, pos, state);
		return !event.isCanceled();
	}

	/** Same real "throwaway enchanted tool, real loot table" technique {@code FocusEffectBreak}'s own instant
	 * break already used - kept here so both the delayed and (if a caller still wants one) instant paths stay
	 * consistent. */
	private static void harvest(Level level, BlockPos pos, BlockState state, Player player, boolean silk, int fortune) {
		ItemStack tool = new ItemStack(Items.NETHERITE_PICKAXE);
		if (silk) {
			tool.enchant(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
		} else if (fortune > 0) {
			tool.enchant(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE), fortune);
		}
		BlockEntity blockEntity = level.getBlockEntity(pos);
		Block.dropResources(state, level, pos, blockEntity, player instanceof LivingEntity living ? living : null, tool);
		level.removeBlock(pos, false);
	}

	// -------------------------------------------------------------------------------------------
	// Delayed block swapping
	// -------------------------------------------------------------------------------------------

	/** Real, direct port of {@code ServerEvents#addSwapper} - after {@code life} real ticks (0 = immediately
	 * on the next scheduler tick), turns {@code pos} into {@code target}'s block (or removes it, if
	 * {@code target} is empty), provided it still matches {@code source} and passes {@code allowSwap}. A
	 * positive {@code life} then spreads to any of the 6 orthogonal neighbors that also match {@code source}
	 * and are "exposed" (touching a non-solid/air neighbor of their own), each queued with {@code life - 1} -
	 * the original's own real "flood outward, one ring per real life-tick" shape (used for things like Salis
	 * Mundus converting a matching material patch, not just a single block). */
	public static void addSwapper(Level level, BlockPos pos, Object source, ItemStack target, boolean consumeTarget, int life, Player player,
			boolean fx, boolean fancy, int color, boolean pickup, boolean silk, int fortune, Predicate<SwapContext> allowSwap, float visCost) {
		if (level.isClientSide) return;
		queueFor(SWAPS, level.dimension()).add(new SwapEntry(pos, source, target, consumeTarget, life, player, fx, fancy, color, pickup, silk, fortune, allowSwap, visCost));
	}

	private static void tickSwaps(Level level, ResourceKey<Level> dim) {
		Queue<SwapEntry> queue = SWAPS.get(dim);
		if (queue == null || queue.isEmpty()) return;
		Queue<SwapEntry> next = new ArrayDeque<>();
		SwapEntry entry;
		while ((entry = queue.poll()) != null) {
			doSwap(level, entry, next);
		}
		SWAPS.put(dim, next);
	}

	private static void doSwap(Level level, SwapEntry entry, Queue<SwapEntry> next) {
		BlockState current = level.getBlockState(entry.pos);
		if (!matches(current, entry.source)) return;
		if (entry.visCost > 0.0f && AuraHandler.getVis(level, entry.pos) < entry.visCost) return;
		if (entry.player != null && !canBreak(level, entry.player, entry.pos, current)) return;
		if (!entry.allowSwap.test(new SwapContext(level, entry.player, entry.pos))) return;

		int slot = -1;
		if (!entry.consumeTarget || entry.target == null || entry.target.isEmpty()) {
			slot = 1;
		} else if (entry.player != null) {
			slot = findMatchingSlot(entry.player, entry.target);
		}
		if (entry.player != null && entry.player.getAbilities().instabuild) slot = 1;
		if (slot < 0) return;

		if (entry.player != null && !entry.player.getAbilities().instabuild) {
			if (entry.consumeTarget) {
				entry.player.getInventory().removeItem(slot, 1);
			}
			if (entry.pickup) {
				dropOrGive(level, entry, current);
			}
			if (entry.visCost > 0.0f) {
				AuraHandler.drainVis(level, entry.pos, entry.visCost, false);
			}
		}

		if (entry.target == null || entry.target.isEmpty()) {
			level.removeBlock(entry.pos, false);
		} else if (entry.target.getItem() instanceof net.minecraft.world.item.BlockItem blockItem) {
			level.setBlockAndUpdate(entry.pos, blockItem.getBlock().defaultBlockState());
		} else {
			level.removeBlock(entry.pos, false);
		}

		if (entry.life <= 0) return; // real match to the original's own "if (vs.lifespan <= 0) continue" - no further spread
		spreadToNeighbors(level, entry, next);
	}

	private static int findMatchingSlot(Player player, ItemStack target) {
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (ItemStack.isSameItemSameComponents(inv.getItem(i), target)) return i;
		}
		return -1;
	}

	/** Same real "throwaway enchanted tool, real loot table" technique {@link #harvest} uses - silk/fortune
	 * are respected by whatever real loot table {@code brokenState}'s block actually has, not a hand-rolled
	 * "does this block support silk touch" check. */
	private static void dropOrGive(Level level, SwapEntry entry, BlockState brokenState) {
		ItemStack tool = new ItemStack(Items.NETHERITE_PICKAXE);
		if (entry.silk) {
			tool.enchant(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
		} else if (entry.fortune > 0) {
			tool.enchant(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE), entry.fortune);
		}
		BlockEntity be = level.getBlockEntity(entry.pos);
		java.util.List<ItemStack> drops = Block.getDrops(brokenState, (net.minecraft.server.level.ServerLevel) level, entry.pos, be, entry.player, tool);
		for (ItemStack stack : drops) {
			if (!entry.player.getInventory().add(stack)) {
				level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level, entry.pos.getX() + 0.5, entry.pos.getY() + 0.5, entry.pos.getZ() + 0.5, stack));
			}
		}
	}

	private static void spreadToNeighbors(Level level, SwapEntry entry, Queue<SwapEntry> next) {
		for (Direction dir : Direction.values()) {
			BlockPos neighborPos = entry.pos.relative(dir);
			BlockState neighborState = level.getBlockState(neighborPos);
			if (!matches(neighborState, entry.source)) continue;
			if (!isExposed(level, neighborPos)) continue;
			next.add(new SwapEntry(neighborPos, entry.source, entry.target, entry.consumeTarget, entry.life - 1, entry.player, entry.fx, entry.fancy,
					entry.color, entry.pickup, entry.silk, entry.fortune, entry.allowSwap, entry.visCost));
		}
	}

	private static boolean isExposed(Level level, BlockPos pos) {
		for (Direction dir : Direction.values()) {
			if (!level.getBlockState(pos.relative(dir)).isCollisionShapeFullBlock(level, pos.relative(dir))) return true;
		}
		return false;
	}

	private static boolean matches(BlockState state, Object source) {
		if (source instanceof BlockState blockState) return state.equals(blockState);
		if (source instanceof TagKey<?> tag) {
			@SuppressWarnings("unchecked")
			TagKey<Block> blockTag = (TagKey<Block>) tag;
			return state.is(blockTag);
		}
		return false;
	}

	// -------------------------------------------------------------------------------------------

	private static <T> Queue<T> queueFor(Map<ResourceKey<Level>, Queue<T>> map, ResourceKey<Level> dim) {
		return map.computeIfAbsent(dim, d -> new ArrayDeque<>());
	}

	/** Real replacement for the original's own {@code ServerEvents.SwapperPredicate} - the context a real
	 * {@code allowSwap} predicate gets to inspect before a swap is allowed to proceed. */
	public record SwapContext(Level level, Player player, BlockPos pos) {
	}

	private static final class BreakEntry {
		final BlockPos pos;
		final BlockState source;
		final Player player;
		final boolean fx;
		final boolean silk;
		final int fortune;
		final float strength;
		float durabilityCurrent;
		final float durabilityMax;
		final float visCost;

		BreakEntry(BlockPos pos, BlockState source, Player player, boolean fx, boolean silk, int fortune, float strength,
				float durabilityCurrent, float durabilityMax, float visCost) {
			this.pos = pos;
			this.source = source;
			this.player = player;
			this.fx = fx;
			this.silk = silk;
			this.fortune = fortune;
			this.strength = strength;
			this.durabilityCurrent = durabilityCurrent;
			this.durabilityMax = durabilityMax;
			this.visCost = visCost;
		}
	}

	private static final class SwapEntry {
		final BlockPos pos;
		final Object source;
		final ItemStack target;
		final boolean consumeTarget;
		int life;
		final Player player;
		final boolean fx;
		final boolean fancy;
		final int color;
		final boolean pickup;
		final boolean silk;
		final int fortune;
		final Predicate<SwapContext> allowSwap;
		final float visCost;

		SwapEntry(BlockPos pos, Object source, ItemStack target, boolean consumeTarget, int life, Player player, boolean fx, boolean fancy,
				int color, boolean pickup, boolean silk, int fortune, Predicate<SwapContext> allowSwap, float visCost) {
			this.pos = pos;
			this.source = source;
			this.target = target;
			this.consumeTarget = consumeTarget;
			this.life = life;
			this.player = player;
			this.fx = fx;
			this.fancy = fancy;
			this.color = color;
			this.pickup = pickup;
			this.silk = silk;
			this.fortune = fortune;
			this.allowSwap = allowSwap;
			this.visCost = visCost;
		}
	}

	private static final class RunnableEntry {
		final Runnable runnable;
		int delay;

		RunnableEntry(Runnable runnable, int delay) {
			this.runnable = runnable;
			this.delay = delay;
		}
	}
}
