package thaumcraft.common.blocks.essentia;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.blocks.ILabelable;
import thaumcraft.common.tiles.essentia.TileAlembic;

/**
 * Replaces the old (1.12.2) {@code BlockAlembic}. A small essentia container fed only by a
 * {@link thaumcraft.common.tiles.essentia.TileSmelter} below it - see {@link TileAlembic}'s own doc comment
 * for the label mechanic and what's simplified about it. Renders its existing static model directly (the
 * original's {@code TileAlembicRenderer} only ever drew the label overlay and a decorative connection
 * "nozzle" on top of the normal block - both purely cosmetic, neither needed for the block to render).
 */
public class BlockAlembic extends Block implements EntityBlock, ILabelable {

	public static final MapCodec<BlockAlembic> CODEC = simpleCodec(BlockAlembic::new);

	private static final VoxelShape SHAPE = Shapes.box(0.0625, 0, 0.0625, 0.9375, 1, 0.9375);

	public BlockAlembic(BlockBehaviour.Properties properties) {
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

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TileAlembic(pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level.isClientSide || !(level.getBlockEntity(pos) instanceof TileAlembic tile)) return InteractionResult.PASS;
		if (player.isCrouching() && tile.getAspectFilter() != null) {
			tile.clearLabel();
			level.playSound(null, pos, SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 1.0f, 1.0f);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	/** {@code ItemLabel} calls this - locks the alembic to one aspect (see {@link TileAlembic#applyLabel}). */
	@Override
	public boolean applyLabel(Player player, BlockPos pos, Direction side, ItemStack labelStack) {
		if (!(player.level().getBlockEntity(pos) instanceof TileAlembic tile)) return false;
		Aspect labelAspect = null;
		if (labelStack.getItem() instanceof thaumcraft.api.aspects.IEssentiaContainerItem container) {
			thaumcraft.api.aspects.AspectList held = container.getAspects(labelStack);
			if (held != null && held.size() > 0) {
				labelAspect = held.getAspectsSortedByAmount()[0];
			}
		}
		if (!tile.applyLabel(labelAspect)) return false;
		player.level().playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.4f, 1.0f);
		return true;
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
		if (!(level.getBlockEntity(pos) instanceof TileAlembic tile)) return 0;
		float ratio = tile.getEssentiaAmount(Direction.UP) / (float) TileAlembic.MAX_AMOUNT;
		return net.minecraft.util.Mth.floor(ratio * 14.0f) + (tile.getEssentiaAmount(Direction.UP) > 0 ? 1 : 0);
	}
}
