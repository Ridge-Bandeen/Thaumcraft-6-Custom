package thaumcraft.api.internal;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType;
import thaumcraft.api.capabilities.IPlayerWarp.EnumWarpType;
import thaumcraft.api.golems.seals.ISeal;
import thaumcraft.api.golems.seals.ISealEntity;
import thaumcraft.api.golems.seals.SealPos;
import thaumcraft.api.golems.tasks.Task;
import thaumcraft.api.research.ResearchCategory;

/**
 * Replaces the old (1.12.2) {@code IInternalMethodHandler} - the interface {@code ThaumcraftApi.internalMethods}
 * is typed as, and the whole reason {@code AuraHelper}/{@code GolemHelper} stayed stubs for most of this
 * port ("designed as a thin pass-through to {@code ThaumcraftApi.internalMethods}, an indirection layer
 * purely so the API package wouldn't need to reference {@code thaumcraft.common}" - see those classes'
 * own doc comments).
 *
 * This port's real implementation is {@link thaumcraft.common.ThaumcraftInternalMethodHandler} -
 * deliberately minimal, not a full recreation of the original: the aura/knowledge/warp methods (already
 * real subsystems in this port - {@code AuraHandler}, {@code ThaumcraftCapabilities}) are wired through for
 * real, but {@code getObjectAspects}/{@code generateTags} (the original's global item-to-aspect database,
 * a project-sized subsystem on its own - see that class's doc comment for why it's out of scope for now)
 * and the seal/golem-task registry methods (no seals/golems ported yet) stay no-ops, same shape as the
 * original's own {@code DummyInternalMethodHandler} for those specific methods.
 */
public interface IInternalMethodHandler {

	/**
	 * Add raw knowledge points (not whole knowledges) to the given player.
	 */
	boolean addKnowledge(Player player, EnumKnowledgeType type, ResearchCategory category, int amount);

	/**
	 * Progresses research - if linked to a multi-stage research entry, advances by one stage (or starts at
	 * stage 1 if not known before).
	 */
	boolean progressResearch(Player player, String researchkey);

	/**
	 * Completes research outright (all stages).
	 */
	boolean completeResearch(Player player, String researchkey);

	/**
	 * @return does the player have all the required knowledge to complete the passed researchkey
	 */
	boolean doesPlayerHaveRequisites(Player player, String researchkey);

	/**
	 * Adds warp to the player.
	 */
	void addWarpToPlayer(Player player, int amount, EnumWarpType type);

	/**
	 * The total of the player's normal + permanent warp. NOT temporary warp.
	 */
	int getActualWarp(Player player);

	AspectList getObjectAspects(ItemStack is);

	AspectList generateTags(ItemStack is);

	/** Non-player entities only - {@link thaumcraft.api.aspects.AspectHelper#getEntityAspects} handles the
	 * player case itself (a random per-name pool, not a registry lookup). May return {@code null}. */
	AspectList getEntityAspects(Entity entity);

	float drainVis(Level level, BlockPos pos, float amount, boolean simulate);

	float drainFlux(Level level, BlockPos pos, float amount, boolean simulate);

	void addVis(Level level, BlockPos pos, float amount);

	void addFlux(Level level, BlockPos pos, float amount, boolean showEffect);

	/**
	 * The aura and flux in a chunk, added together.
	 */
	float getTotalAura(Level level, BlockPos pos);

	float getVis(Level level, BlockPos pos);

	float getFlux(Level level, BlockPos pos);

	int getAuraBase(Level level, BlockPos pos);

	boolean shouldPreserveAura(Level level, Player player, BlockPos pos);

	void registerSeal(ISeal seal);

	ISeal getSeal(String key);

	ISealEntity getSealEntity(ResourceKey<Level> dim, SealPos pos);

	void addGolemTask(ResourceKey<Level> dim, Task task);

	ItemStack getSealStack(String key);
}
