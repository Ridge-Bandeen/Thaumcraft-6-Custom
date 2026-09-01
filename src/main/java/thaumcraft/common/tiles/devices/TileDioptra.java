package thaumcraft.common.tiles.devices;

import java.util.Arrays;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import thaumcraft.common.registry.ThaumcraftBlockEntities;
import thaumcraft.common.world.aura.AuraChunk;
import thaumcraft.common.world.aura.AuraHandler;

/**
 * Replaces the old (1.12.2) {@code TileDioptra} - an aura radar that scans a 13x13 grid of chunks around
 * itself every 20 ticks, recording each one's local vis (when {@code ENABLED}) or flux (when disabled,
 * toggled by right-clicking the block - see {@link thaumcraft.common.blocks.devices.BlockDioptra}) as a
 * single byte 0-64, normalized against a fixed 500-vis/flux reference scale. The center cell
 * ({@code gridAmount[84]}) doubles as this block's own comparator output.
 *
 * Dropped {@code grid_amt_p} - the original declared and initialized a second identical array but never
 * read or wrote it anywhere past the constructor; genuinely dead code, not a missed feature.
 *
 * <b>Not ported: the radar's visual rendering.</b> The original's {@code TileDioptraRenderer} is a
 * fixed-function-OpenGL {@code TileEntitySpecialRenderer} building a dynamic heightmap mesh every frame
 * (raw {@code GL11}/{@code GlStateManager} calls, an API 1.21.1 no longer has) - a real rewrite onto
 * {@code BlockEntityRenderer}/{@code PoseStack}/{@code VertexConsumer}, not a mechanical port. Matches this
 * session's consistent boundary so far (no custom tile/entity renderer has been ported yet at all - every
 * device so far has stayed within static blockstate models). The scan data, comparator output, and vis/flux
 * toggle are all fully functional and tested; only the in-world radar visualization is missing.
 */
public class TileDioptra extends BlockEntity {

	public int counter;
	public byte[] gridAmount = new byte[169];

	public TileDioptra(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.DIOPTRA.get(), pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TileDioptra tile) {
		if (level.isClientSide) return;
		tile.counter++;
		if (tile.counter % 20 != 0) return;

		boolean visMode = state.getValue(BlockStateProperties.ENABLED);
		Arrays.fill(tile.gridAmount, (byte) 0);
		for (int xx = 0; xx < 13; xx++) {
			for (int zz = 0; zz < 13; zz++) {
				AuraChunk ac = AuraHandler.getAuraChunk(level.dimension(), (pos.getX() >> 4) + xx - 6, (pos.getZ() >> 4) + zz - 6);
				if (ac == null) continue;
				float amount = visMode ? ac.getVis() : ac.getFlux();
				tile.gridAmount[xx + zz * 13] = (byte) Math.min(64.0f, amount / 500.0f * 64.0f);
			}
		}
		tile.setChanged();
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		if (tag.contains("grid_a")) {
			gridAmount = tag.getByteArray("grid_a");
		}
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putByteArray("grid_a", gridAmount);
	}
}
