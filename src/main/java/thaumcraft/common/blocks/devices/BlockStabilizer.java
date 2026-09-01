package thaumcraft.common.blocks.devices;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.api.crafting.IInfusionStabiliserExt;

/**
 * Replaces the old (1.12.2) {@code BlockStabilizer}. A small decorative pillar placed near an infusion
 * altar (see {@link thaumcraft.common.tiles.crafting.TileInfusionMatrix#getSurroundings}) to passively
 * boost crafting stability - {@code getSurroundings} already checks any block generically via
 * {@code instanceof IInfusionStabiliser(Ext)}, so this is a pure drop-in with no changes needed there.
 *
 * Simplified for this pass: the original's real class ({@code TileStabilizer}) was a full block entity with
 * an unrelated second job - slowly charging an "energy" meter from ambient aura (via
 * {@code AuraHelper.polluteAura}) and spending it to pacify nearby {@code EntityFluxRift} mobs, with the
 * meter's charge also driving a client-side color tint on the model. That whole mechanic is provably
 * decoupled from the block's actual stabilizing behavior - {@code canStabaliseInfusion}/
 * {@code getStabilizationAmount} in the original never read the energy field at all - and {@code EntityFluxRift}
 * isn't ported, so it's cut entirely rather than half-built. No block entity is needed as a result: both
 * {@code IInfusionStabiliser} methods only need the block instance, not any per-placement state.
 */
public class BlockStabilizer extends Block implements IInfusionStabiliserExt {

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
}
