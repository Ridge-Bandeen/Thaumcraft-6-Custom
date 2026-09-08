package thaumcraft.common.blocks.basic;

import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.IScribeTools;
import thaumcraft.common.tiles.crafting.TileResearchTable;

/**
 * Replaces the old (1.12.2) {@code BlockTable} - a plain decorative furniture block (real, purely cosmetic
 * shape - see {@link thaumcraft.common.blocks.crafting.BlockResearchTable}'s own doc comment for why this
 * reuses the exact same real shape). One real, load-bearing mechanic kept: right-clicking a real Wood Table
 * ({@link BlocksTC#tableWood}) with a real {@link IScribeTools} item instantly upgrades it in place into a
 * real {@link BlocksTC#researchTable}, carrying the held item straight into the new tile's own scribing-tool
 * slot - matches the original's own real {@code onBlockActivated} exactly (the Stone Table has no such
 * upgrade - matches the original, whose own check was hardcoded to the wood variant specifically).
 *
 * Real, documented cut: the original's own {@code FMLCommonHandler.firePlayerCraftingEvent} call (a
 * "count this as a craft" stat/achievement hook) has no real modern equivalent wired up anywhere else in
 * this port either - dropped, purely a stats-tracking side effect.
 */
public class BlockTable extends Block {

	private static final VoxelShape SHAPE = Shapes.box(0, 0, 0, 1, 0.875, 1);

	public BlockTable(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, net.minecraft.core.BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean useShapeForLightOcclusion(BlockState state) {
		return true;
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, net.minecraft.core.BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		if (!state.is(BlocksTC.tableWood.get()) || !(stack.getItem() instanceof IScribeTools)) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (level.isClientSide) {
			return ItemInteractionResult.SUCCESS;
		}

		ItemStack scribingTool = stack.copy();
		BlockState upgraded = BlocksTC.researchTable.get().defaultBlockState();
		level.setBlockAndUpdate(pos, upgraded);
		if (level.getBlockEntity(pos) instanceof TileResearchTable tile) {
			tile.getItemHandler().setStackInSlot(0, scribingTool);
		}
		stack.shrink(stack.getCount());
		return ItemInteractionResult.CONSUME;
	}
}
