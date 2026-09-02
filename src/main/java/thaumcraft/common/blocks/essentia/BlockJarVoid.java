package thaumcraft.common.blocks.essentia;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.common.registry.ThaumcraftBlockEntities;
import thaumcraft.common.tiles.essentia.TileJarFillable;
import thaumcraft.common.tiles.essentia.TileJarFillableVoid;

/**
 * Replaces the old (1.12.2) {@code jarVoid} instance of {@code BlockJar} (the original shared one generic
 * block class across every jar tile-entity variant via a constructor parameter - already split into one
 * real class per variant this session for {@code BlockTubeFilter}/{@code BlockTubeRestrict}, same reason
 * here). Everything about how a jar is interacted with (right-click fill, sneak-empty into the aura, label
 * locking) is inherited unchanged from {@link BlockJar} - it's all written generically against
 * {@link TileJarFillable}, the shared supertype. Only the tile-entity construction and ticker need
 * overriding for the real, separate {@link ThaumcraftBlockEntities#JAR_VOID} type - see
 * {@link TileJarFillableVoid}'s own doc comment for the actual void behavior.
 */
public class BlockJarVoid extends BlockJar {

	public static final MapCodec<BlockJarVoid> CODEC = simpleCodec(BlockJarVoid::new);

	public BlockJarVoid(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TileJarFillableVoid(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == ThaumcraftBlockEntities.JAR_VOID.get()
				? (lvl, pos, st, be) -> TileJarFillable.tick(lvl, pos, st, (TileJarFillable) be)
				: null;
	}
}
