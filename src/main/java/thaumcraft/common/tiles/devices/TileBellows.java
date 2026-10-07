package thaumcraft.common.tiles.devices;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * <p>Real client-only "breathing bag" animation restored 2026-09-17 ({@link #inflation}) - re-evaluating this
 * class's own earlier "dropped, no custom renderer" cut per this port's own standing "reevaluate past cuts
 * once their blocker resolves" rule (a real {@link net.minecraft.client.renderer.blockentity.BlockEntityRenderer}
 * precedent has long since existed elsewhere in this port). No new sync wiring was needed for this one - unlike
 * most of this port's other renderer restorations, {@link #inflation} is driven purely off
 * {@link BlockStateProperties#ENABLED}, which is already a normal {@code BlockState} property and thus already
 * synced to the client for free. See {@link thaumcraft.client.render.TileBellowsRenderer}'s own doc comment for
 * the real rendering side.
 */
public class TileBellows extends BlockEntity {

	private int delay;

	/** Client-only "breathing bag" animation state - real direct port of the true original's own exact
	 * quick-deflate/slow-inflate cycle. Never synced (purely client-computed off the already-synced
	 * {@link BlockStateProperties#ENABLED} state). */
	private float inflation = 1.0f;
	private boolean inflating;
	private boolean firstRun = true;

	public TileBellows(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.BELLOWS.get(), pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TileBellows tile) {
		if (level.isClientSide) {
			tile.tickInflation(level, pos, state);
			return;
		}
		if (!state.getValue(BlockStateProperties.ENABLED)) {
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
		// addon hooks (another mod's devices that take air from bellows)
		if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
			thaumcraft.api.BellowsHooks.pulse(serverLevel, pos, facing);
		}
	}

	private void tickInflation(Level level, BlockPos pos, BlockState state) {
		if (!state.getValue(BlockStateProperties.ENABLED)) {
			return;
		}
		if (firstRun) {
			inflation = 0.35f + level.random.nextFloat() * 0.55f;
			firstRun = false;
		}
		if (inflation > 0.35f && !inflating) {
			inflation -= 0.075f;
		}
		if (inflation <= 0.35f && !inflating) {
			inflating = true;
		}
		if (inflation < 1.0f && inflating) {
			inflation += 0.025f;
		}
		if (inflation >= 1.0f && inflating) {
			inflating = false;
			level.playLocalSound(pos.getX(), pos.getY(), pos.getZ(), SoundEvents.GHAST_SHOOT, SoundSource.BLOCKS,
					0.01f, 0.5f + (level.random.nextFloat() - level.random.nextFloat()) * 0.2f, false);
		}
	}

	public float getInflation() {
		return inflation;
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
