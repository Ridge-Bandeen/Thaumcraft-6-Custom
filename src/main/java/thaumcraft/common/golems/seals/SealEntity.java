package thaumcraft.common.golems.seals;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import thaumcraft.api.golems.GolemHelper;
import thaumcraft.api.golems.seals.ISeal;
import thaumcraft.api.golems.seals.ISealConfigArea;
import thaumcraft.api.golems.seals.ISealConfigToggles;
import thaumcraft.api.golems.seals.ISealEntity;
import thaumcraft.api.golems.seals.SealPos;
import thaumcraft.api.golems.tasks.Task;

/**
 * Replaces the old (1.12.2) {@code SealEntity} - the real, placed instance of an {@link ISeal} at one
 * {@link SealPos}. Direct translation: {@link ISealConfigArea} seals get a real default 3x3(x1) area
 * (1 wide along whichever axis the seal's own face points), redstone-sensitivity suspends every task tied
 * to this seal the moment it goes powered rather than removing them outright.
 *
 * Real, documented cut: {@link #syncToClient} is a real no-op - the original's {@code PacketSealToClient}
 * existed purely to drive in-world seal-icon rendering and a config GUI, neither of which exist in this
 * port yet (same "no custom renderer, no GUI screen yet" boundary the whole golem arc has kept to).
 */
public class SealEntity implements ISealEntity {

	private SealPos sealPos;
	private ISeal seal;
	private byte priority;
	private byte color;
	private boolean locked;
	private boolean redstone;
	private String owner = "";
	private boolean stopped;
	private BlockPos area = new BlockPos(1, 1, 1);

	/** For {@link #readNBT} - the seal type/position are restored from the tag itself. */
	public SealEntity() {
	}

	public SealEntity(SealPos sealPos, ISeal seal) {
		this.sealPos = sealPos;
		this.seal = seal;
		if (seal instanceof ISealConfigArea) {
			int x = sealPos.face.getStepX() == 0 ? 3 : 1;
			int y = sealPos.face.getStepY() == 0 ? 3 : 1;
			int z = sealPos.face.getStepZ() == 0 ? 3 : 1;
			area = new BlockPos(x, y, z);
		}
	}

	@Override
	public void tickSealEntity(Level level) {
		if (seal == null) return;
		if (isStoppedByRedstone(level)) {
			if (!stopped) {
				for (Task t : GolemHelper.getTasks(level.dimension()).values()) {
					if (t.getSealPos() != null && t.getSealPos().equals(sealPos)) {
						t.setSuspended(true);
					}
				}
			}
			stopped = true;
			return;
		}
		stopped = false;
		seal.tickSeal(level, this);
	}

	@Override
	public boolean isStoppedByRedstone(Level level) {
		return isRedstoneSensitive() && (level.hasNeighborSignal(sealPos.pos) || level.hasNeighborSignal(sealPos.pos.relative(sealPos.face)));
	}

	@Override
	public ISeal getSeal() {
		return seal;
	}

	@Override
	public SealPos getSealPos() {
		return sealPos;
	}

	@Override
	public byte getPriority() {
		return priority;
	}

	@Override
	public void setPriority(byte priority) {
		this.priority = priority;
	}

	@Override
	public byte getColor() {
		return color;
	}

	@Override
	public void setColor(byte color) {
		this.color = color;
	}

	@Override
	public String getOwner() {
		return owner;
	}

	@Override
	public void setOwner(String owner) {
		this.owner = owner;
	}

	@Override
	public boolean isLocked() {
		return locked;
	}

	@Override
	public void setLocked(boolean locked) {
		this.locked = locked;
	}

	@Override
	public boolean isRedstoneSensitive() {
		return redstone;
	}

	@Override
	public void setRedstoneSensitive(boolean redstone) {
		this.redstone = redstone;
	}

	@Override
	public BlockPos getArea() {
		return area;
	}

	@Override
	public void setArea(BlockPos v) {
		area = v;
	}

	@Override
	public void readNBT(CompoundTag nbt) {
		BlockPos p = BlockPos.of(nbt.getLong("pos"));
		net.minecraft.core.Direction face = net.minecraft.core.Direction.values()[nbt.getByte("face")];
		sealPos = new SealPos(p, face);
		setPriority(nbt.getByte("priority"));
		setColor(nbt.getByte("color"));
		setLocked(nbt.getBoolean("locked"));
		setRedstoneSensitive(nbt.getBoolean("redstone"));
		setOwner(nbt.getString("owner"));
		seal = GolemHelper.createSeal(nbt.getString("type"));
		if (seal != null) {
			seal.readCustomNBT(nbt);
			if (seal instanceof ISealConfigArea) {
				area = BlockPos.of(nbt.getLong("area"));
			}
			if (seal instanceof ISealConfigToggles toggles) {
				for (ISealConfigToggles.SealToggle prop : toggles.getToggles()) {
					if (nbt.contains(prop.getKey())) {
						prop.setValue(nbt.getBoolean(prop.getKey()));
					}
				}
			}
		}
	}

	@Override
	public CompoundTag writeNBT() {
		CompoundTag nbt = new CompoundTag();
		nbt.putLong("pos", sealPos.pos.asLong());
		nbt.putByte("face", (byte) sealPos.face.ordinal());
		nbt.putString("type", seal.getKey());
		nbt.putByte("priority", getPriority());
		nbt.putByte("color", getColor());
		nbt.putBoolean("locked", isLocked());
		nbt.putBoolean("redstone", isRedstoneSensitive());
		nbt.putString("owner", getOwner());
		if (seal != null) {
			seal.writeCustomNBT(nbt);
			if (seal instanceof ISealConfigArea) {
				nbt.putLong("area", area.asLong());
			}
			if (seal instanceof ISealConfigToggles toggles) {
				for (ISealConfigToggles.SealToggle prop : toggles.getToggles()) {
					nbt.putBoolean(prop.getKey(), prop.getValue());
				}
			}
		}
		return nbt;
	}

	@Override
	public void syncToClient(Level level) {
		// no seal-icon renderer or config GUI exists yet - see this class's own doc comment.
	}
}
