package thaumcraft.common.blocks.misc;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.blocks.crafting.BlockGolemBuilder;

/**
 * The original's BlockPlaceholder: the blocks a Salis Mundus ritual turned into a Golem Press (iron bars, cauldron, anvil,
 * stone table) or an Infernal Furnace (nether bricks, obsidian). Invisible - the device's own model draws the whole
 * structure - and immovable. Using a press part opens the press; breaking any part takes the device apart: it drops
 * its real block (the loot tables say which) and the rest turn back into theirs ({@link BlockGolemBuilder#dismantle},
 * {@link thaumcraft.common.blocks.devices.BlockInfernalFurnace#destroyFurnace}). The cauldron glows (light 13).
 */
public class BlockPlaceholder extends Block {

	public BlockPlaceholder(BlockBehaviour.Properties properties) {
		super(properties.noOcclusion().pushReaction(PushReaction.BLOCK));
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	private boolean isFurnacePart(BlockState state) {
		return state.is(BlocksTC.placeholderNetherbrick.get()) || state.is(BlocksTC.placeholderObsidian.get());
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (isFurnacePart(state)) return InteractionResult.PASS;
		BlockPos press = findPress(level, pos);
		if (press == null) return InteractionResult.PASS;
		if (level.isClientSide) return InteractionResult.SUCCESS;
		if (level.getBlockEntity(press) instanceof thaumcraft.common.tiles.crafting.TileGolemBuilder tile) {
			player.openMenu(tile);
		}
		return InteractionResult.CONSUME;
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock()) && !level.isClientSide && !isFurnacePart(state)) {
			BlockPos press = findPress(level, pos);
			if (press != null) BlockGolemBuilder.dismantle(level, press, pos);
		}
		super.onRemove(state, level, pos, newState, movedByPiston);
	}

	/** A furnace part takes the furnace apart only when broken by a player or an explosion - its teardown leaves lava,
	 * which a plain block replacement (world edits, test cleanup) must not. */
	@Override
	public void destroy(net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockState state) {
		super.destroy(level, pos, state);
		if (level instanceof Level l) breakFurnaceAround(l, pos, state);
	}

	@Override
	public void wasExploded(Level level, BlockPos pos, net.minecraft.world.level.Explosion explosion) {
		super.wasExploded(level, pos, explosion);
		breakFurnaceAround(level, pos, null);
	}

	private void breakFurnaceAround(Level level, BlockPos pos, BlockState state) {
		if (level.isClientSide || (state != null && !isFurnacePart(state))) return;
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
			BlockState s = level.getBlockState(p);
			if (s.is(BlocksTC.infernalFurnace.get())) {
				thaumcraft.common.blocks.devices.BlockInfernalFurnace.destroyFurnace(level, p.immutable(), s, pos);
				return;
			}
		}
	}

	private static BlockPos findPress(Level level, BlockPos pos) {
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
			if (level.getBlockState(p).is(BlocksTC.golemBuilder.get())) return p.immutable();
		}
		return null;
	}
}
