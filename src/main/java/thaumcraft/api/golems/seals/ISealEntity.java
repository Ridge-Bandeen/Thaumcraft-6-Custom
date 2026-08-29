package thaumcraft.api.golems.seals;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;


public interface ISealEntity {

	public void tickSealEntity(Level world);

	public ISeal getSeal();

	public SealPos getSealPos();

	public byte getPriority();

	public void setPriority(byte priority);

	public void readNBT(CompoundTag nbt);

	public CompoundTag writeNBT();

	public void syncToClient(Level world);

	public BlockPos getArea();

	public void setArea(BlockPos v);

	boolean isLocked();

	void setLocked(boolean locked);

	public boolean isRedstoneSensitive();

	public void setRedstoneSensitive(boolean redstone);

	String getOwner();

	void setOwner(String owner);

	public byte getColor();

	public void setColor(byte color);

	public boolean isStoppedByRedstone(Level world);

}
