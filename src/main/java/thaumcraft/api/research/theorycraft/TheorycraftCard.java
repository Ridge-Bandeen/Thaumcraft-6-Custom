package thaumcraft.api.research.theorycraft;

import java.util.Arrays;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Replaces the old (1.12.2) {@code TheorycraftCard} - direct translation, unchanged shape. See
 * {@code CardAnalyze} for a real example.
 */
public abstract class TheorycraftCard {

	private long seed = -1;

	/** A seed value used to determine random attributes associated with the card. */
	public long getSeed() {
		if (seed < 0) setSeed(System.nanoTime());
		return seed;
	}

	/** Run when the card is initially created. If it returns false the card is discarded and a new one
	 * created in its place. */
	public boolean initialize(Player player, ResearchTableData data) {
		return true;
	}

	/** If true this card cannot come up in the normal draw rotation - it only appears if added by an aid. */
	public boolean isAidOnly() {
		return false;
	}

	/** How much inspiration this card costs to activate. Can be zero. Negative numbers return inspiration. */
	public abstract int getInspirationCost();

	/** The research category this card is associated with. Can be null if it is not linked to anything. */
	public String getResearchCategory() {
		return null;
	}

	public abstract String getLocalizedName();

	public abstract String getLocalizedText();

	/** Items required to complete this operation - null items in the array display as a "?" in the GUI.
	 * Consuming/checking them is the card's own {@link #activate} responsibility; non-null items are
	 * handled automatically. */
	public ItemStack[] getRequiredItems() {
		return null;
	}

	/** Will the listed items be consumed when the card is picked. */
	public boolean[] getRequiredItemsConsumed() {
		if (getRequiredItems() != null) {
			boolean[] b = new boolean[getRequiredItems().length];
			Arrays.fill(b, false);
			return b;
		}
		return null;
	}

	/** Performs the card's functionality on the current table data - real item checks/consumption are the
	 * card's own responsibility here. */
	public abstract boolean activate(Player player, ResearchTableData data);

	/** Internal use only - this should not be called unless you want to mess things up. */
	public void setSeed(long seed) {
		this.seed = Math.abs(seed);
	}

	public CompoundTag serialize() {
		CompoundTag nbt = new CompoundTag();
		nbt.putLong("seed", seed);
		return nbt;
	}

	public void deserialize(CompoundTag nbt) {
		if (nbt == null) return;
		seed = nbt.getLong("seed");
	}
}
