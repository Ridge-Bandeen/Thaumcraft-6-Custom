package thaumcraft.common.blocks.devices;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.api.crafting.IInfusionStabiliserExt;

/**
 * Replaces the old (1.12.2) {@code BlockInlay} ("Redstone Inlay"). A thin decorative floor plate, another
 * weak passive stabilizer for the infusion altar (like {@link BlockStabilizer}, which this is a cheaper
 * cousin of) - {@code TileInfusionMatrix#getSurroundings} already picks up any {@code IInfusionStabiliser}
 * block generically, so this needs no changes there either.
 *
 * Simplified for this pass, same shape as {@code BlockStabilizer}'s own cut: the original was a redstone-
 * wire-style block with four connecting-texture properties (propagating a 0-15 "charge" outward from a
 * {@code BlockStabilizer} source, redstone-dust style) plus a matching multipart blockstate. That whole
 * system was cosmetic-only - {@code canStabaliseInfusion}/{@code getStabilizationAmount} in the original
 * are unconditional/flat and never read the charge - and it only ever fed a particle effect
 * ({@code randomDisplayTick}) and the (also unbuilt) {@code TileStabilizer}-charge-driven instability-
 * mitigation path documented in {@code TileInfusionMatrix}'s own doc comment. Ported as a plain stateless
 * {@code Block}, reusing the original's existing "isolated dot" model/texture as-is (no wire-connection
 * variants) rather than porting the four-property multipart blockstate for a cosmetic-only payoff.
 */
public class BlockInlay extends Block implements IInfusionStabiliserExt {

	public static final MapCodec<BlockInlay> CODEC = simpleCodec(BlockInlay::new);

	private static final VoxelShape SHAPE = Shapes.box(0, 0, 0, 1, 0.0625, 1);

	public BlockInlay(BlockBehaviour.Properties properties) {
		super(properties);
	}

	/** The original's charge tint for the inlay's glowing top layer (tint index 0); {@link BlockStabilizer} used
	 * the same formula for its own energy. Charge 0 is a dim grey, 1-15 runs from half to full brightness. */
	public static int colorMultiplier(int charge) {
		float f = charge == 0 ? 0.3f : charge / 15.0f * 0.5f + 0.5f;
		int c = Math.max(0, Math.min((int) (f * 255.0f), 255));
		return 0xFF000000 | c << 16 | c << 8 | c;
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
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
	}

	@Override
	protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
		if (direction == Direction.DOWN && !canSurvive(state, level, pos)) {
			return Blocks.AIR.defaultBlockState();
		}
		return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
	}

	@Override
	public boolean canStabaliseInfusion(Level world, BlockPos pos) {
		return true;
	}

	@Override
	public float getStabilizationAmount(Level world, BlockPos pos) {
		return 0.025f;
	}
}
