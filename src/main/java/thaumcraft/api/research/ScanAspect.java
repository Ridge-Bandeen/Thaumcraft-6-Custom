package thaumcraft.api.research;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectHelper;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType;

/**
 * Replaces the old (1.12.2) {@code ScanAspect} - a scan target that matches "does this thing carry aspect
 * X", checking a live entity via {@link AspectHelper#getEntityAspects} or an item/block via
 * {@link AspectHelper#getObjectAspects}. Both were permanent stubs when the original {@code Aspect}
 * constructor was first ported (its own doc comment flagged this exact class as the follow-up, once
 * {@code ScanningManager} existed) - real now that {@code ThaumcraftEntityTags}/{@code ThaumcraftObjectTags}
 * back those lookups for real (see {@link AspectHelper}'s own doc comment).
 *
 * Reachable today through {@code ItemThaumometer}'s two real dispatch paths: right-clicking a living entity
 * (a live {@link Entity}) or a block (a {@link BlockPos}) with a Thaumometer. The item/{@code ItemEntity}
 * matching branches are also real and portable but not currently reachable the same way {@code ScanItem}'s
 * own doc comment already notes - {@code ItemThaumometer} never hands a raw {@link ItemStack} to
 * {@code ScanningManager}.
 */
public class ScanAspect implements IScanThing {

	private final String research;
	private final Aspect aspect;

	/**
	 * A trigger research is not auto-registered per aspect the way the original suggested ("you should not
	 * have to add your own entry... a trigger research is added automatically for each aspect") - that
	 * auto-registration lived in the same {@code Aspect} constructor call site this class's own port
	 * unblocks, and porting it is a separate, larger follow-up (37 real aspects x a real live registration
	 * each) than this one scan target. Pass a real research key explicitly for now.
	 */
	public ScanAspect(String research, Aspect aspect) {
		this.research = research;
		this.aspect = aspect;
	}

	@Override
	public boolean checkThing(Player player, Object obj) {
		if (obj == null) {
			return false;
		}
		AspectList aspects;
		if (obj instanceof Entity entity && !(obj instanceof ItemEntity)) {
			aspects = AspectHelper.getEntityAspects(entity);
		} else {
			ItemStack stack = null;
			if (obj instanceof ItemStack itemStack) {
				stack = itemStack;
			} else if (obj instanceof ItemEntity itemEntity && !itemEntity.getItem().isEmpty()) {
				stack = itemEntity.getItem();
			} else if (obj instanceof BlockPos pos) {
				BlockState state = player.level().getBlockState(pos);
				if (!state.isAir()) {
					stack = new ItemStack(state.getBlock());
				}
			}
			aspects = stack != null ? AspectHelper.getObjectAspects(stack) : null;
		}
		return aspects != null && aspects.getAmount(aspect) > 0;
	}

	@Override
	public void onSuccess(Player player, Object object) {
		ThaumcraftApi.internalMethods.addKnowledge(player, EnumKnowledgeType.OBSERVATION, ResearchCategories.getResearchCategory("AUROMANCY"), 1);
		ThaumcraftApi.internalMethods.addKnowledge(player, EnumKnowledgeType.OBSERVATION, ResearchCategories.getResearchCategory("BASICS"), 1);
		ThaumcraftApi.internalMethods.addKnowledge(player, EnumKnowledgeType.OBSERVATION, ResearchCategories.getResearchCategory("ALCHEMY"), 1);
	}

	@Override
	public String getResearchKey(Player player, Object object) {
		return research;
	}
}
