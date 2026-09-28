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
 * {@link thaumcraft.common.lib.crafting.ThaumcraftEntityTags} the same way. The seal/golem registry methods
 * stay no-ops (no seals/golems ported yet), same as the original's own {@code DummyInternalMethodHandler}.</li>
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

	/** {@code sync}: push the player's knowledge to their client afterwards - the original did ({@code syncResearch}); without it the
	 * client never learned of a scan's result, so the Thaumometer glow on already-scanned things never went away. */
	private boolean progressResearch(Player player, String researchkey, boolean sync) {
		boolean progressed = progressResearchNoSync(player, researchkey);
		if (progressed && sync && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
			ThaumcraftCapabilities.getKnowledge(player).sync(serverPlayer);
		}
		return progressed;
	}

	private boolean progressResearchNoSync(Player player, String researchkey) {
		IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(player);
		ResearchEntry entry = ResearchCategories.getResearch(researchkey);
		ResearchStage[] stages = entry != null ? entry.getStages() : null;
		// A key with no research entry at all - the per-item/per-mob "!..." scan keys - is a one-shot flag: known means done. (Nothing
		// capped it, so scanning the same thing forever said "learned something new" and kept handing out knowledge.)
		if (stages == null && researchkey != null && researchkey.startsWith("!") && knowledge.isResearchKnown(researchkey)) return false;
		// Only a real, loaded multi-stage entry has an actual cap to refuse progressing past - an
		// entry-less key (a "!virtualFlag", or an ad-hoc test/reference key with no ResearchEntry at all)
		// has no such cap, matching the old placeholder implementation's fully unconditional increment for
		// every key. completeResearch (below) is what stops calling this repeatedly for those.
		if (stages != null && knowledge.isResearchComplete(researchkey)) return false;
		if (!doesPlayerHaveRequisites(player, researchkey)) return false;

		if (!knowledge.isResearchKnown(researchkey)) knowledge.addResearch(researchkey);

		int stage = Math.max(0, knowledge.getResearchStage(researchkey));
		if (stages != null) {
			ResearchStage completedStage = stage > 0 ? stages[Math.min(stage, stages.length) - 1] : null;
			// Restored 2026-09-27 - the original's own real auto-skip (ResearchManager#progressResearch): a stage with
			// NO requirement fields at all is silently counted as done in the same call rather than needing its own
			// separate click. Without this, EVERY research entry (not just gated ones) would need one extra click to
			// finish - the vast majority of entries are one stage with no requirements at all, and used to instant-
			// complete on a single click only because the old code path here ignored per-stage gating entirely.
			boolean autoSkip = (stages.length == 1 && stage == 0 && hasNoRequirements(stages[0]))
					|| (stages.length > 1 && stages.length <= stage + 1 && stage < stages.length && hasNoRequirements(stages[stage]));
			int cs = stage + (autoSkip ? 1 : 0);
			int newStage = Math.min(stages.length + 1, cs + 1);
			knowledge.setResearchStage(researchkey, newStage);
			if (completedStage != null && completedStage.getWarp() > 0) {
				addWarpToPlayer(player, completedStage.getWarp(), IPlayerWarp.EnumWarpType.NORMAL);
			}
			if (newStage > stages.length) {
				// Real "new research" badge - the hex-grid browser's own real per-entry/per-category
				// indicator. The original's paired POPUP flag (triggers an in-game GUI popup) is dropped -
				// no popup UI exists in this port.
				knowledge.setResearchFlag(researchkey, IPlayerKnowledge.EnumResearchFlag.RESEARCH);
			}
		} else {
			knowledge.setResearchStage(researchkey, Math.max(1, stage + 1));
		}

		if (entry != null && entry.getSiblings() != null) {
			for (String sibling : entry.getSiblings()) {
				if (!knowledge.isResearchComplete(sibling) && doesPlayerHaveRequisites(player, sibling)) {
					completeResearch(player, sibling);
				}
			}
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
		while (progressResearch(player, researchkey, false)) any = true;
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
		ThaumcraftCapabilities.getWarp(player).add(type, amount);
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
		// showEffect (flux smoke/sound) is client FX, not ported - same simplification as everywhere else.
		AuraHandler.addFlux(level, pos, amount);
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
		// No seals ported yet.
	}

	@Override
	public ISeal getSeal(String key) {
		return null;
	}

	@Override
	public ISealEntity getSealEntity(ResourceKey<Level> dim, SealPos pos) {
		return null;
	}

	@Override
	public void addGolemTask(ResourceKey<Level> dim, Task task) {
		// No golems ported yet.
	}

	@Override
	public ItemStack getSealStack(String key) {
		return null;
	}
}
