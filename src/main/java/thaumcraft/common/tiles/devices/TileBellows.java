package thaumcraft.common.tiles.devices;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import thaumcraft.common.registry.ThaumcraftBlockEntities;

/**
 * Replaces the old (1.12.2) {@code TileBellows} - every 2 ticks, while enabled (unpowered), nudges an
 * already-cooking vanilla furnace in front of it one tick closer to finishing, a simple "speeds up smelting"
 * utility. {@code AbstractFurnaceBlockEntity#cookingProgress}/{@code cookingTotalTime} are package-private in
 * modern Minecraft (public in 1.12.2's {@code TileEntityFurnace}) - widened via
 * {@code src/main/resources/META-INF/accesstransformer.cfg}, the first access transformer this port has
 * needed. {@code cookingTotalTime} (not a hardcoded 200, unlike the original's own {@code ct < 199} check) is
 * used so this still behaves correctly against a modded/data-pack-changed smelting recipe with a different
 * total cook time.
 *
 * Dropped: the client-side "breathing bag" animation (a render-bounding-box-driven scale value the original
 * tracked purely for its custom TESR) and {@code getBellows} (a static "how many enabled bellows point at
 * this position" helper with no caller anywhere in the original's own shipped code - presumably meant for an
 * unbuilt multiblock furnace tier). No custom {@link net.minecraft.client.renderer.blockentity.BlockEntityRenderer}
 * for this device - same "keep the function, skip the custom renderer" precedent every device this session
 * has kept, now including its own animation.
 */
public class TileBellows extends BlockEntity {

	private int delay;

	public TileBellows(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.BELLOWS.get(), pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TileBellows tile) {
		if (level.isClientSide || !state.getValue(BlockStateProperties.ENABLED)) {
			return;
		}
		if (++tile.delay < 2) {
			return;
		}
		tile.delay = 0;

		Direction facing = state.getValue(BlockStateProperties.FACING);
		if (level.getBlockEntity(pos.relative(facing)) instanceof AbstractFurnaceBlockEntity furnace) {
			int progress = furnace.cookingProgress;
			int total = furnace.cookingTotalTime;
			if (progress > 0 && progress < total - 1) {
				furnace.cookingProgress = progress + 1;
				furnace.setChanged();
			}
		}
	}
}
