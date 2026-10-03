package thaumcraft.common.blocks.misc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Replaces the old (1.12.2) {@code BlockNitor} - one small floating light source per {@link DyeColor} (16
 * blocks, same "one class, one instance per real DyeColor" registration shape
 * {@code thaumcraft.common.blocks.basic.BlockCandle}'s own real port already established). Small non-solid
 * collision box, no block below required (unlike Candle it just floats), full light level.
 *
 * Invisible, like the original: the visible flame and core are particles drawn by
 * {@link thaumcraft.common.tiles.misc.TileNitor} every client tick (restored 2026-09-25 - the port had dropped the
 * tile, so the block drew a stretched cross model plus a sparse vanilla flame).
 */
public class BlockNitor extends Block implements net.minecraft.world.level.block.EntityBlock {

	private static final VoxelShape SHAPE = Shapes.box(0.33, 0.33, 0.33, 0.66, 0.66, 0.66);

	public final DyeColor dye;

	public BlockNitor(BlockBehaviour.Properties properties, DyeColor dye) {
		super(properties);
		this.dye = dye;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
		return net.minecraft.world.level.block.RenderShape.INVISIBLE;
	}

	@org.jetbrains.annotations.Nullable
	@Override
	public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new thaumcraft.common.tiles.misc.TileNitor(pos, state);
	}

	@org.jetbrains.annotations.Nullable
	@Override
	public <T extends net.minecraft.world.level.block.entity.BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
		if (type != thaumcraft.common.registry.ThaumcraftBlockEntities.NITOR.get()) return null;
		if (level.isClientSide) {
			return (lvl, pos, st, be) -> thaumcraft.common.tiles.misc.TileNitor.clientTick(lvl, pos, st, (thaumcraft.common.tiles.misc.TileNitor) be);
		}
		// nothing of Thaumcraft's own runs here; addons' hooks (thaumcraft.api.NitorHooks) do
		return thaumcraft.api.NitorHooks.hasServerTickers() ? thaumcraft.api.NitorHooks::tick : null;
	}
}
