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
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.common.world.aura.AuraHandler;

/**
 * This port's real {@link IInternalMethodHandler} - installed over {@code ThaumcraftApi.internalMethods}'
 * default {@code DummyInternalMethodHandler} in {@code Thaumcraft}'s constructor. Deliberately minimal, not
 * a full recreation of the original (see the interface's own doc comment for the overall reasoning):
 *
 * <ul>
 * <li>Knowledge/research/warp methods wire straight through to {@link ThaumcraftCapabilities}'s already-real
 * {@code IPlayerKnowledge}/{@code IPlayerWarp} attachments - no GUI popups/sounds (the original's "with all
 * the proper bells and whistles"), just the raw state change.</li>
 * <li>Aura methods wire straight through to the already-real {@link AuraHandler} - this is what finally
 * lets {@code thaumcraft.api.aura.AuraHelper} stop being a permanent stub.</li>
 * <li>{@code doesPlayerHaveRequisites} can't check real prerequisites without a loaded research-entry
 * registry (not ported - see the port-tracking memory notes on the research/Thaumonomicon UI still being
 * blocked) - always returns {@code true} (permissive), matching this project's "don't block content behind
 * an unbuilt subsystem" precedent (e.g. the FIRSTSTEPS@2 default knowledge).</li>
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
		ThaumcraftCapabilities.getKnowledge(player).addKnowledge(type, category, amount);
		return true;
	}

	@Override
	public boolean progressResearch(Player player, String researchkey) {
		IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(player);
		int stage = knowledge.getResearchStage(researchkey);
		return knowledge.setResearchStage(researchkey, Math.max(1, stage + 1));
	}

	@Override
	public boolean completeResearch(Player player, String researchkey) {
		// This port's PlayerKnowledge treats "known" and "complete" as the same thing (no loaded
		// ResearchEntry stage counts to compare against - see that class's own doc comment), so this and
		// addResearch are equivalent here.
		return ThaumcraftCapabilities.getKnowledge(player).addResearch(researchkey);
	}

	@Override
	public boolean doesPlayerHaveRequisites(Player player, String researchkey) {
		return true;
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
