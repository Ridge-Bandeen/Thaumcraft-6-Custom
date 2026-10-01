package thaumcraft.common.blocks.devices;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.api.crafting.IInfusionStabiliserExt;
import thaumcraft.common.registry.ThaumcraftBlockEntities;
import thaumcraft.common.tiles.devices.TileStabilizer;

/**
 * Replaces the old (1.12.2) {@code BlockStabilizer}. A small decorative pillar placed near an infusion
 * altar (see {@link thaumcraft.common.tiles.crafting.TileInfusionMatrix#getSurroundings}) to passively
 * boost crafting stability - {@code getSurroundings} already checks any block generically via
 * {@code instanceof IInfusionStabiliser(Ext)}, so this is a pure drop-in with no changes needed there.
 *
 * <p>Real {@link TileStabilizer}, restored 2026-09-15 - an earlier pass here had cut it entirely, reasoning
 * that its real energy meter was provably decoupled from this block's own stabilizing score (true - see
 * {@code canStabaliseInfusion}/{@code getStabilizationAmount} below, both still real and flat) and that
 * {@code EntityFluxRift} wasn't ported (false by the time this was re-checked - it's been real for a while).
 * See {@link TileStabilizer}'s own doc comment for the real mechanics this unlocks. Its stored energy tints the
 * model (ThaumcraftClient's block colour handler, via {@link BlockInlay#colorMultiplier}).
 */
public class BlockStabilizer extends Block implements IInfusionStabiliserExt, EntityBlock {

	public static final MapCodec<BlockStabilizer> CODEC = simpleCodec(BlockStabilizer::new);

	private static final VoxelShape SHAPE = Shapes.or(
			Shapes.box(0.0625, 0, 0.0625, 0.9375, 0.25, 0.9375),
			Shapes.box(0.1875, 0.25, 0.1875, 0.8125, 0.6875, 0.8125),
			Shapes.box(0.3125, 0.5, 0.3125, 0.6875, 1.0, 0.6875)
	);

	public BlockStabilizer(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public boolean canStabaliseInfusion(Level world, BlockPos pos) {
		return true;
	}

	@Override
	public float getStabilizationAmount(Level world, BlockPos pos) {
		return 0.25f;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TileStabilizer(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == ThaumcraftBlockEntities.STABILIZER.get()
				? (lvl, pos, st, be) -> TileStabilizer.tick(lvl, pos, st, (TileStabilizer) be)
				: null;
	}
}
