package thaumcraft.common.blocks.essentia;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import thaumcraft.common.registry.ThaumcraftBlockEntities;
import thaumcraft.common.tiles.essentia.TileJarFillable;

/**
 * Replaces the old (1.12.2) {@code BlockJar} (the {@code jar_normal}/essentia-storage instance of it - the
 * original shared this class with the golem "brain jar" variant via a generic tile-class constructor param;
 * golems aren't built yet, so this port only has the essentia jar). See {@link TileJarFillable}'s own doc
 * comment for what's simplified.
 *
 * Filling is two-handed: passively from directly above via {@link TileJarFillable}'s own tick (any adjacent
 * {@code IEssentiaTransport}), or directly by right-clicking with an essentia-container item (anything
 * implementing {@link IEssentiaContainerItem}, e.g. Crystal Essence) - the same "read the held item's real
 * aspects and consume it" pattern {@code BlockCrucible} already established, reused here since Crystal
 * Essence already gives this a real, reachable fill path in survival right now.
 */
public class BlockJar extends Block implements EntityBlock {

	public static final MapCodec<BlockJar> CODEC = simpleCodec(BlockJar::new);

	private static final VoxelShape SHAPE = Shapes.box(0.1875, 0.0, 0.1875, 0.8125, 0.75, 0.8125);

	public BlockJar(BlockBehaviour.Properties properties) {
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
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		if (level.isClientSide) return ItemInteractionResult.sidedSuccess(true);
		if (!(level.getBlockEntity(pos) instanceof TileJarFillable tile)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

		if (player.isShiftKeyDown() && stack.isEmpty()) {
			tile.emptyIntoAura(level, pos);
			level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.4f, 1.0f);
			return ItemInteractionResult.SUCCESS;
		}

		if (stack.getItem() instanceof IEssentiaContainerItem container) {
			AspectList held = container.getAspects(stack);
			if (held != null && held.size() > 0) {
				var sorted = held.getAspectsSortedByAmount();
				var aspect = sorted[0];
				int amount = held.getAmount(aspect);
				if (tile.doesContainerAccept(aspect)) {
					int leftover = tile.addToContainer(aspect, amount);
					if (leftover < amount) {
						if (!player.getAbilities().instabuild) {
							stack.shrink(1);
						}
						level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.4f, 1.0f);
						return ItemInteractionResult.SUCCESS;
					}
				}
			}
		}

		return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TileJarFillable(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == ThaumcraftBlockEntities.JAR.get()
				? (lvl, pos, st, be) -> TileJarFillable.tick(lvl, pos, st, (TileJarFillable) be)
				: null;
	}
}
