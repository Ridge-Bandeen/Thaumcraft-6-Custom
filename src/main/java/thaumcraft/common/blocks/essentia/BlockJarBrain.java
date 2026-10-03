package thaumcraft.common.blocks.essentia;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import thaumcraft.common.registry.ThaumcraftBlockEntities;
import thaumcraft.common.tiles.devices.TileJarBrain;
import thaumcraft.common.tiles.essentia.TileJarFillable;

/**
 * Replaces the old (1.12.2) {@code BlockJar}'s brain-jar instance - the true original shared one
 * {@code BlockJar} class between the essentia jar and this via a generic tile-class constructor param; this
 * port already splits that into dedicated classes ({@link BlockJar} for essentia, this for the brain jar),
 * matching the "each mechanically-distinct device gets its own real tile/block pair" convention this whole
 * port has followed since {@code BlockMirrorEssentia} split off from the plain {@code BlockMirror}. See
 * {@link TileJarBrain}'s own doc comment for the real mechanic and what's simplified.
 *
 * <p>Restored 2026-09-27: breaking a brain jar now keeps its accumulated XP on the dropped item (see
 * {@link #getDrops}/{@link JarBrainBlockItem}), matching the original's own item-tag round trip.
 */
public class BlockJarBrain extends Block implements EntityBlock {

	public static final MapCodec<BlockJarBrain> CODEC = simpleCodec(BlockJarBrain::new);

	private static final VoxelShape SHAPE = Shapes.box(0.1875, 0.0, 0.1875, 0.8125, 0.875, 0.8125);

	public BlockJarBrain(BlockBehaviour.Properties properties) {
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
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!(level.getBlockEntity(pos) instanceof TileJarBrain tile)) return InteractionResult.PASS;
		if (level.isClientSide) {
			level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.2f, 1.0f);
			return InteractionResult.SUCCESS;
		}
		tile.drainRandom((ServerLevel) level, pos, level.getRandom());
		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
		if (!(level.getBlockEntity(pos) instanceof TileJarBrain tile)) return 0;
		float fraction = tile.getXp() / (float) TileJarBrain.XP_MAX;
		return Math.min(15, Mth.floor(fraction * 14.0f) + (tile.getXp() > 0 ? 1 : 0));
	}

	/** As the original's spawnBrainJar: a harvested (or blown up) brain jar keeps its XP; nothing drops when it's
	 * simply removed (creative, commands). */
	@Override
	protected java.util.List<net.minecraft.world.item.ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
		net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(this);
		if (params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY) instanceof TileJarBrain tile && tile.getXp() > 0) {
			stack.set(thaumcraft.api.items.ThaumcraftDataComponents.JAR_XP.get(), tile.getXp());
		}
		return java.util.List.of(stack);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TileJarBrain(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == ThaumcraftBlockEntities.JAR_BRAIN.get()
				? (lvl, pos, st, be) -> TileJarBrain.tick(lvl, pos, st, (TileJarBrain) be)
				: null;
	}

	/** As the original, a Brain in a Jar is worth five bookshelves to an enchanting table. */
	@Override
	public float getEnchantPowerBonus(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos pos) {
		return 5.0f;
	}
}
