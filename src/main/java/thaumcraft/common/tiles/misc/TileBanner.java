package thaumcraft.common.tiles.misc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.registry.ThaumcraftBlockEntities;

/**
 * Replaces the old (1.12.2) {@code TileBanner} - the real backing tile of {@link
 * thaumcraft.common.blocks.misc.BlockBannerTC}. Real facing (0/4/8/12 = the four cardinal directions, same
 * encoding the original used), real wall-vs-floor state, and a real optional {@link Aspect} tag settable
 * via right-clicking with a Phial (see that block's own doc comment).
 */
public class TileBanner extends BlockEntity {

	private byte facing;
	private boolean onWall;
	private Aspect aspect;

	public TileBanner(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.BANNER.get(), pos, state);
	}

	public byte getBannerFacing() {
		return facing;
	}

	public void setBannerFacing(byte facing) {
		this.facing = facing;
		setChanged();
	}

	public boolean getWall() {
		return onWall;
	}

	public void setWall(boolean wall) {
		this.onWall = wall;
		setChanged();
	}

	public Aspect getAspect() {
		return aspect;
	}

	public void setAspect(Aspect aspect) {
		this.aspect = aspect;
		setChanged();
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		facing = tag.getByte("facing");
		onWall = tag.getBoolean("wall");
		aspect = tag.contains("aspect") ? Aspect.getAspect(tag.getString("aspect")) : null;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putByte("facing", facing);
		tag.putBoolean("wall", onWall);
		if (aspect != null) {
			tag.putString("aspect", aspect.getTag());
		}
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	@Override
	public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
		return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
	}
}
