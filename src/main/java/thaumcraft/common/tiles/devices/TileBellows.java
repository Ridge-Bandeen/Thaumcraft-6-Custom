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
 * <p><b>Correction (found while reviewing this port's own "dead/blocked" candidates, see the port-tracking
 * memory):</b> {@link #getBellows} was previously dropped here with the doc-comment claim that it "had no
 * caller anywhere in the original's own shipped code" - false. The true pre-port original's own
 * {@code TileSmelter#checkNeighbours} calls it directly (confirmed via
 * {@code git show 31baee9:...TileSmelter.java}), for the real bellows speed-boost on the Smelter (see
 * {@link thaumcraft.common.tiles.essentia.TileSmelter}'s own doc comment) - it just hadn't been wired up yet
 * because {@code TileSmelter} was ported before this method's real caller was re-checked. Restored below,
 * same real formula/signature as the original.
 *
 * <p>Dropped: the client-side "breathing bag" animation (a render-bounding-box-driven scale value the
 * original tracked purely for its custom TESR). No custom
 * {@link net.minecraft.client.renderer.blockentity.BlockEntityRenderer} for this device - same "keep the
 * function, skip the custom renderer" precedent every device this session has kept, now including its own
 * animation.
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

	/**
	 * How many enabled bellows, among the given candidate directions from {@code pos}, are a real
	 * {@link TileBellows} facing back toward {@code pos}. Real direct port of the true original's own static
	 * helper - see this class's own doc comment for why it was previously (incorrectly) dropped as dead code.
	 */
	public static int getBellows(Level level, BlockPos pos, Direction[] directions) {
		int bellows = 0;
		for (Direction dir : directions) {
			if (level.getBlockEntity(pos.relative(dir)) instanceof TileBellows
					&& level.getBlockState(pos.relative(dir)).getValue(BlockStateProperties.FACING) == dir.getOpposite()
					&& level.getBlockState(pos.relative(dir)).getValue(BlockStateProperties.ENABLED)) {
				bellows++;
			}
		}
		return bellows;
	}
}
