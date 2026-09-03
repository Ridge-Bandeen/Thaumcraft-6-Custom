package thaumcraft.common.blocks.misc;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.items.consumables.ItemPhial;
import thaumcraft.common.tiles.misc.TileBanner;

/**
 * Replaces the old (1.12.2) {@code BlockBannerTC} - scoped here to just what {@code
 * thaumcraft.common.entities.monster.boss.EntityCultistPortalGreater} actually places, the "Crimson Cult"
 * banner ({@code dye == null} in the original's own terms). The original also registered 16 plain dye-
 * colored banner variants sharing this same class (a separate, unrelated player-craftable decoration
 * feature) - not ported alongside this one, since nothing in this port needs them yet; this class's own
 * dye-tinting branch (dead code for a null-dye banner - {@code getColor()} always returns "no tint" in that
 * case) is dropped for the same reason.
 *
 * Real phial interaction, matching the original exactly: sneak-right-click clears a set {@link
 * thaumcraft.api.aspects.Aspect} tag, right-clicking with a filled {@link ItemPhial} sets one (consuming
 * the phial). {@code RenderShape.INVISIBLE} - like the original, the real visible geometry is drawn entirely
 * by {@link thaumcraft.client.render.TileBannerRenderer}, not a normal blockstate model.
 */
public class BlockBannerTC extends Block implements EntityBlock {

	public static final MapCodec<BlockBannerTC> CODEC = simpleCodec(BlockBannerTC::new);

	private static final VoxelShape SHAPE = Shapes.block();

	public BlockBannerTC(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	/** Real, matches the original's own {@code getCollisionBoundingBox() -> null} - no collision at all,
	 * players and mobs walk straight through it (only {@link #getShape} - used for the outline/selection
	 * box, simplified here to a plain full cube rather than the original's own facing-dependent variants -
	 * needs real geometry). */
	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TileBanner(pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		return interact(level, pos, player, ItemStack.EMPTY);
	}

	@Override
	protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		InteractionResult result = interact(level, pos, player, stack);
		return result == InteractionResult.SUCCESS ? net.minecraft.world.ItemInteractionResult.SUCCESS
				: net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	private InteractionResult interact(Level level, BlockPos pos, Player player, ItemStack held) {
		if (!(level.getBlockEntity(pos) instanceof TileBanner banner)) return InteractionResult.PASS;
		if (!player.isShiftKeyDown() && !(held.getItem() instanceof ItemPhial)) return InteractionResult.PASS;
		if (level.isClientSide) return InteractionResult.SUCCESS;

		if (player.isShiftKeyDown()) {
			banner.setAspect(null);
		}
		else {
			AspectList aspects = ((ItemPhial) held.getItem()).getAspects(held);
			if (aspects == null || aspects.getAspects().length == 0) return InteractionResult.PASS;
			banner.setAspect(aspects.getAspects()[0]);
			held.shrink(1);
		}
		level.playSound(null, pos, SoundEvents.WOOL_HIT, SoundSource.BLOCKS, 1.0f, 1.0f);
		return InteractionResult.SUCCESS;
	}

	/** Real, minor simplification vs the original: drops a plain {@code ItemStack}, not one carrying the
	 * tile's own stored aspect tag forward (the original preserved that via NBT on the dropped item) - a
	 * real, small flavor loss, not a functional one (the block itself still works identically once
	 * replaced). */
	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock())) {
			net.minecraft.world.entity.item.ItemEntity out = new net.minecraft.world.entity.item.ItemEntity(level,
					pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new net.minecraft.world.item.ItemStack(this));
			level.addFreshEntity(out);
		}
		super.onRemove(state, level, pos, newState, movedByPiston);
	}
}
