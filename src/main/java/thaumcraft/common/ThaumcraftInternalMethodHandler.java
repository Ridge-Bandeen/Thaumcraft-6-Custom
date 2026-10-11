package thaumcraft.common;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.capabilities.IPlayerWarp;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.golems.seals.ISeal;
import thaumcraft.api.golems.seals.ISealEntity;
import thaumcraft.api.golems.seals.SealPos;
import thaumcraft.api.golems.tasks.Task;
import thaumcraft.api.internal.IInternalMethodHandler;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.api.research.ResearchStage;
import thaumcraft.common.world.aura.AuraHandler;

/**
 * This port's real {@link IInternalMethodHandler} - installed over {@code ThaumcraftApi.internalMethods}'
 * default {@code DummyInternalMethodHandler} in {@code Thaumcraft}'s constructor. Deliberately minimal, not
 * a full recreation of the original (see the interface's own doc comment for the overall reasoning):
 *
 * <ul>
 * <li>Knowledge/warp methods wire straight through to {@link ThaumcraftCapabilities}'s already-real
 * {@code IPlayerKnowledge}/{@code IPlayerWarp} attachments - no GUI popups/sounds (the original's "with all
 * the proper bells and whistles"), just the raw state change.</li>
 * <li>{@code progressResearch}/{@code completeResearch}/{@code doesPlayerHaveRequisites} are now real,
 * multi-stage-aware, {@link ResearchCategories}-backed logic - ported from the original (stale, deleted)
 * {@code thaumcraft.common.lib.research.ResearchManager}'s {@code progressResearch}/{@code
 * doesPlayerHaveRequisites} now that {@code ThaumcraftResearchLoader} actually populates real
 * {@link ResearchEntry} data. Dropped from that original logic: the network-synced GUI popup flags/sounds
 * (no research popup UI yet) and the {@code required_item}/{@code required_craft} stage gates (display-only
 * without the crafting/possession-triggered auto-progression hook, itself a real, separate, not-yet-ported
 * system) - a stage with only those gates set is simply never auto-completed by this port yet.</li>
 * <li>Aura methods wire straight through to the already-real {@link AuraHandler} - this is what finally
 * lets {@code thaumcraft.api.aura.AuraHelper} stop being a permanent stub.</li>
 * <li>{@code getObjectAspects}/{@code generateTags} now wire through to
 * {@link thaumcraft.common.lib.crafting.ThaumcraftObjectTags} - see that class's own doc comment for what's
 * simplified in the object-tag database it replaces. {@code getEntityAspects} wires through to its sibling
 * {@link thaumcraft.common.lib.crafting.ThaumcraftEntityTags} the same way. The seal/golem methods forward to the
 * real registries ({@code GolemHelper}, {@code TaskHandler}) - they were left as no-ops from before golems existed
 * until 2026-09-29.</li>
 * </ul>
 */
public class ThaumcraftInternalMethodHandler implements IInternalMethodHandler {

	@Override
	public boolean addKnowledge(Player player, IPlayerKnowledge.EnumKnowledgeType type, ResearchCategory category, int amount) {
		thaumcraft.api.capabilities.IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(player);
		ResearchCategory realCategory = type.hasFields() ? category : null;
		int before = knowledge.getKnowledge(type, realCategory);
		knowledge.addKnowledge(type, realCategory, amount);
		int gained = knowledge.getKnowledge(type, realCategory) - before;
		if (gained > 0 && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
			broadcastKnowledgeGain(serverPlayer, type, realCategory, gained);
		}
		return true;
	}

	/** Real client-bound "you gained a point of knowledge" notification, restored 2026-09-24 - see {@link
	 * thaumcraft.common.lib.network.misc.PacketKnowledgeGain}'s own doc comment for why this fires once PER
	 * POINT of real post-clamp delta (matching the true original's own real {@code ResearchManager
	 * #addKnowledge} loop exactly), not once per call. */
	private static void broadcastKnowledgeGain(net.minecraft.server.level.ServerPlayer player,
			IPlayerKnowledge.EnumKnowledgeType type, ResearchCategory category, int gained) {
		try {
			var payload = new thaumcraft.common.lib.network.misc.PacketKnowledgeGain((byte) type.ordinal(), category == null ? "" : category.key);
			for (int a = 0; a < gained; a++) {
				net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
			}
		} catch (Exception e) {
			thaumcraft.Thaumcraft.LOGGER.debug("Knowledge gain FX packet failed to send (no real client connection?)", e);
		}
	}

	@Override
	public boolean progressResearch(Player player, String researchkey) {
		return progressResearch(player, researchkey, true);
	}

	/** The original's own static {@code ResearchManager.noFlags}: set by the Thaumonomicon before a player-driven advance
	 * (both its "open" and "study" clicks sent {@code noFlags=true}) so research the player just clicked through
	 * doesn't also get a "new research" badge; consumed (reset) by the next completion, exactly as there. */
	public static boolean noFlags = false;

	/** Pushes the player's knowledge to their client afterwards - the original did ({@code syncResearch}); without it the
	 * client never learned of a scan's result, so the Thaumometer glow on already-scanned things never went away. */
	private boolean progressResearch(Player player, String researchkey, boolean push) {
		boolean progressed = progressResearchNoSync(player, researchkey, true);
		if (progressed && push && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
			ThaumcraftCapabilities.getKnowledge(player).sync(serverPlayer);
		}
		return progressed;
	}

	/**
	 * A faithful port of the original's own {@code ResearchManager#progressResearch(player, key, sync)} (restored in full
	 * 2026-09-27 - an earlier version here was a simplification that added a completed stage's warp as plain NORMAL warp,
	 * dropped the completion popup, addendum notices and the 5 XP, and always badged new research):
	 * <ul>
	 * <li>the auto-skip: a stage with none of the four requirement kinds completes in the same call;</li>
	 * <li>warp: the stage just completed, plus - when the entry finishes - the final stage (the original's own
	 * arithmetic, which counts the final stage twice when it is also the one just completed); split half permanent, half
	 * normal (all permanent when it is 1), none in wuss mode;</li>
	 * <li>on finishing ({@code sync}): the POPUP flag (the client shows a "Research Completed!" toast for it), the
	 * RESEARCH ("new") badge unless {@link #noFlags}, reward items/knowledge; then, for every completed entry with an
	 * addendum that waits on this key, a chat notice and a PAGE badge;</li>
	 * <li>siblings complete alongside; 5 experience points per advance of a real entry.</li>
	 * </ul>
	 */
	private boolean progressResearchNoSync(Player player, String researchkey, boolean sync) {
		IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(player);
		ResearchEntry entry = ResearchCategories.getResearch(researchkey);
		ResearchStage[] stages = entry != null ? entry.getStages() : null;
		// A key with no research entry at all - the per-item/per-mob "!..." scan keys - is a one-shot flag: known means done.
		// (The original's own isResearchComplete treats a known entry-less key as complete for the same reason.)
		if (stages == null && researchkey != null && researchkey.startsWith("!") && knowledge.isResearchKnown(researchkey)) return false;
		if (stages != null && knowledge.isResearchComplete(researchkey)) return false;
		if (!doesPlayerHaveRequisites(player, researchkey)) return false;

		if (!knowledge.isResearchKnown(researchkey)) knowledge.addResearch(researchkey);

		boolean popups = true;
		if (stages != null) {
			int cs = Math.max(0, knowledge.getResearchStage(researchkey));
			ResearchStage currentStage = null;
			if (cs > 0) {
				cs = Math.min(cs, stages.length);
				currentStage = stages[cs - 1];
			}
			if (stages.length == 1 && cs == 0 && hasNoRequirements(stages[0])) {
				++cs;
			} else if (stages.length > 1 && stages.length <= cs + 1 && cs < stages.length && hasNoRequirements(stages[cs])) {
				++cs;
			}
			knowledge.setResearchStage(researchkey, Math.min(stages.length + 1, cs + 1));
			popups = cs >= stages.length;
			int warp = currentStage != null ? currentStage.getWarp() : 0;
			if (popups) {
				cs = Math.min(cs, stages.length);
				currentStage = stages[cs - 1];
			}
			if (currentStage != null) {
				warp += currentStage.getWarp();
				if (warp > 0 && !thaumcraft.common.config.ThaumcraftConfig.WUSS_MODE.get() && !player.level().isClientSide) {
					if (warp > 1) {
						int w2 = warp / 2;
						if (warp - w2 > 0) addWarpToPlayer(player, warp - w2, IPlayerWarp.EnumWarpType.PERMANENT);
						if (w2 > 0) addWarpToPlayer(player, w2, IPlayerWarp.EnumWarpType.NORMAL);
					} else {
						addWarpToPlayer(player, warp, IPlayerWarp.EnumWarpType.PERMANENT);
					}
				}
			}
		} else if (entry == null) {
			// an entry-less virtual key: its own one-shot "stage", as before
			knowledge.setResearchStage(researchkey, Math.max(1, knowledge.getResearchStage(researchkey) + 1));
		}

		if (popups) {
			if (sync) {
				knowledge.setResearchFlag(researchkey, IPlayerKnowledge.EnumResearchFlag.POPUP);
				if (!noFlags) {
					knowledge.setResearchFlag(researchkey, IPlayerKnowledge.EnumResearchFlag.RESEARCH);
				} else {
					noFlags = false;
				}
				if (entry != null && entry.getRewardItem() != null) {
					for (net.minecraft.world.item.ItemStack reward : entry.getRewardItem()) {
						if (!player.getInventory().add(reward.copy())) player.drop(reward.copy(), false);
					}
				}
				if (entry != null && entry.getRewardKnow() != null) {
					for (ResearchStage.Knowledge rk : entry.getRewardKnow()) {
						addKnowledge(player, rk.type, rk.category, rk.type.getProgression() * rk.amount);
					}
				}
			}
			for (ResearchCategory category : ResearchCategories.researchCategories.values()) {
				for (ResearchEntry other : category.research.values()) {
					if (other == null || other.getAddenda() == null || !knowledge.isResearchComplete(other.getKey())) continue;
					for (thaumcraft.api.research.ResearchAddendum addendum : other.getAddenda()) {
						if (addendum.getResearch() != null && java.util.Arrays.asList(addendum.getResearch()).contains(researchkey)) {
							player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("tc.addaddendum", other.getLocalizedName()));
							knowledge.setResearchFlag(other.getKey(), IPlayerKnowledge.EnumResearchFlag.PAGE);
							break;
						}
					}
				}
			}
		}

		if (entry != null && entry.getSiblings() != null) {
			for (String sibling : entry.getSiblings()) {
				if (!knowledge.isResearchComplete(sibling) && doesPlayerHaveRequisites(player, sibling)) {
					while (progressResearchNoSync(player, sibling, sync)) {
						// complete the sibling, same as the original's completeResearch(player, sibling, sync)
					}
				}
			}
		}
		if (sync && entry != null) {
			player.giveExperiencePoints(5);
		}
		return true;
	}

	/** A stage with none of the original's own four gate kinds at all - the condition {@code ResearchManager
	 * #progressResearch} auto-skips on, see the caller's own doc comment. */
	private static boolean hasNoRequirements(ResearchStage stage) {
		return stage.getCraft() == null && stage.getObtain() == null && stage.getKnow() == null && stage.getResearch() == null;
	}

	@Override
	public boolean completeResearch(Player player, String researchkey) {
		ResearchEntry entry = ResearchCategories.getResearch(researchkey);
		if (entry == null || entry.getStages() == null) {
			// No real stage count to loop through - one progressResearch call is the whole "completion" (and,
			// since progressResearch never refuses an entry-less key on its own, looping here would never
			// terminate).
			return progressResearch(player, researchkey);
		}
		boolean any = false;
		while (progressResearchNoSync(player, researchkey, true)) any = true;
		if (any && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
			ThaumcraftCapabilities.getKnowledge(player).sync(serverPlayer);
		}
		return any;
	}

	@Override
	public boolean doesPlayerHaveRequisites(Player player, String researchkey) {
		ResearchEntry entry = ResearchCategories.getResearch(researchkey);
		if (entry == null) return true;
		String[] parents = entry.getParentsStripped();
		return parents == null || ThaumcraftCapabilities.knowsResearchStrict(player, parents);
	}

	@Override
	public void addWarpToPlayer(Player player, int amount, IPlayerWarp.EnumWarpType type) {
		// The original's InternalMethodHandler#addWarpToPlayer, in full (restored 2026-09-29 - it had only been adding
		// the number, so the warp counter that drives warp events never moved, the WARP research never unlocked, and
		// the client never heard of it).
		if (player.level().isClientSide) return;
		IPlayerWarp pw = ThaumcraftCapabilities.getWarp(player);
		int cur = pw.get(type);
		// Removing more warp than there is takes it to 0. (The original's clamp set +cur, so it ADDED the current amount
		// instead - fixed at the user's request, 2026-09-29.)
		if (amount < 0 && cur + amount < 0) amount = -cur;
		pw.add(type, amount);
		if (amount > 0) {
			pw.setCounter(pw.get(IPlayerWarp.EnumWarpType.TEMPORARY) + pw.get(IPlayerWarp.EnumWarpType.PERMANENT) + pw.get(IPlayerWarp.EnumWarpType.NORMAL));
		}
		if (type != IPlayerWarp.EnumWarpType.TEMPORARY && ThaumcraftCapabilities.knowsResearchStrict(player, "FIRSTSTEPS")
				&& !ThaumcraftCapabilities.knowsResearchStrict(player, "WARP")) {
			completeResearch(player, "WARP");
			player.displayClientMessage(net.minecraft.network.chat.Component.translatable("research.WARP.warn"), true);
		}
		if (player instanceof net.minecraft.server.level.ServerPlayer sp && pw instanceof thaumcraft.common.lib.research.PlayerWarp warp) {
			warp.send(sp, (byte) type.ordinal(), amount); // PERMANENT 0, NORMAL 1, TEMPORARY 2 - the original's message types
		}
	}

	@Override
	public int getActualWarp(Player player) {
		IPlayerWarp warp = ThaumcraftCapabilities.getWarp(player);
		return warp.get(IPlayerWarp.EnumWarpType.PERMANENT) + warp.get(IPlayerWarp.EnumWarpType.NORMAL);
	}

	@Override
	public AspectList getObjectAspects(ItemStack is) {
		return thaumcraft.common.lib.crafting.ThaumcraftObjectTags.getObjectTags(is);
	}

	@Override
	public AspectList generateTags(ItemStack is) {
		// The original's version of this also registered the derived result for future lookups (its
		// recipe-tracing fallback is real work worth doing once, not every call) - not needed here since
		// ThaumcraftObjectTags#getObjectTags has no tracing step to cache the output of; it's already a
		// flat, cheap map+tag lookup every time.
		return thaumcraft.common.lib.crafting.ThaumcraftObjectTags.getObjectTags(is);
	}

	@Override
	public AspectList getEntityAspects(Entity entity) {
		return thaumcraft.common.lib.crafting.ThaumcraftEntityTags.getEntityTags(entity);
	}

	@Override
	public float drainVis(Level level, BlockPos pos, float amount, boolean simulate) {
		return AuraHandler.drainVis(level, pos, amount, simulate);
	}

	@Override
	public float drainFlux(Level level, BlockPos pos, float amount, boolean simulate) {
		return AuraHandler.drainFlux(level, pos, amount, simulate);
	}

	@Override
	public void addVis(Level level, BlockPos pos, float amount) {
		AuraHandler.addVis(level, pos, amount);
	}

	@Override
	public void addFlux(Level level, BlockPos pos, float amount, boolean showEffect) {
		if (level.isClientSide) return;
		AuraHandler.addFlux(level, pos, amount);
		// as the original: flux smoke where it went in
		if (showEffect && amount > 0.0f && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
			try {
				net.neoforged.neoforge.network.PacketDistributor.sendToPlayersNear(serverLevel, null, pos.getX(), pos.getY(), pos.getZ(), 32.0,
						thaumcraft.common.lib.network.fx.PacketFXPollute.of(pos, amount));
			} catch (Exception e) {
				thaumcraft.Thaumcraft.LOGGER.debug("Pollute FX packet failed to send (no real client connection?)", e);
			}
		}
	}

	@Override
	public float getTotalAura(Level level, BlockPos pos) {
		return AuraHandler.getTotalAura(level, pos);
	}

	@Override
	public float getVis(Level level, BlockPos pos) {
		return AuraHandler.getVis(level, pos);
	}

	@Override
	public float getFlux(Level level, BlockPos pos) {
		return AuraHandler.getFlux(level, pos);
	}

	@Override
	public int getAuraBase(Level level, BlockPos pos) {
		return AuraHandler.getAuraBase(level, pos);
	}

	@Override
	public boolean shouldPreserveAura(Level level, Player player, BlockPos pos) {
		return AuraHandler.shouldPreserveAura(level, player, pos);
	}

	@Override
	public void registerSeal(ISeal seal) {
		thaumcraft.api.golems.GolemHelper.registerSeal(seal);
	}

	@Override
	public ISeal getSeal(String key) {
		return thaumcraft.api.golems.GolemHelper.getSeal(key);
	}

	@Override
	public ISealEntity getSealEntity(ResourceKey<Level> dim, SealPos pos) {
		return thaumcraft.api.golems.GolemHelper.getSealEntity(dim, pos);
	}

	@Override
	public void addGolemTask(ResourceKey<Level> dim, Task task) {
		thaumcraft.common.golems.tasks.TaskHandler.addTask(dim, task);
	}

	@Override
	public ItemStack getSealStack(String key) {
		for (net.minecraft.world.item.Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
			if (item instanceof thaumcraft.common.golems.ItemSealPlacer placer && placer.getSealKey().equals(key)) return new ItemStack(item);
		}
		return ItemStack.EMPTY;
	}
}
