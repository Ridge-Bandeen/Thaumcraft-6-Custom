package thaumcraft.common.tiles.crafting;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.ItemStackHandler;

import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.items.IScribeTools;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.theorycraft.ITheorycraftAid;
import thaumcraft.api.research.theorycraft.ResearchTableData;
import thaumcraft.api.research.theorycraft.TheorycraftManager;
import thaumcraft.common.container.ResearchTableMenu;
import thaumcraft.common.registry.ThaumcraftBlockEntities;

/**
 * Replaces the old (1.12.2) {@code TileResearchTable} - headless data model and real crafting logic only,
 * no block/menu/screen yet (see {@link ResearchTableData}'s own doc comment for the rest of this arc).
 * Purpose-built {@link BlockEntity}+{@link ItemStackHandler}, same "no shared inventory-tile base" pattern
 * every other device this session has used - slot 0 = scribing tools (ink), slot 1 = paper.
 *
 * {@link #data} (the live in-progress theory) deliberately has no {@code BlockEntity}-level sync override
 * (no {@code getUpdateTag} of its own) - unlike a device's inventory slots, this is fundamentally
 * "keep an open GUI in sync with every card draw/pick", which belongs at the menu level (explicit
 * per-action sync to the viewing player, the same shape {@code ArcaneWorkbenchMenu}'s own
 * {@code broadcastChanges} already uses) rather than infrequent generic chunk-level sync - real sync is a
 * job for the menu commit still to come, not this one. World-save persistence ({@link #saveAdditional}/
 * {@link #loadAdditional}) is real here regardless.
 */
public class TileResearchTable extends BlockEntity implements MenuProvider {

	private final ItemStackHandler items = new ItemStackHandler(2) {
		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return switch (slot) {
				case 0 -> stack.getItem() instanceof IScribeTools;
				case 1 -> stack.getItem() == Items.PAPER;
				default -> false;
			};
		}

		@Override
		protected void onContentsChanged(int slot) {
			setChanged();
		}
	};

	public ResearchTableData data;

	public TileResearchTable(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.RESEARCH_TABLE.get(), pos, state);
	}

	public ItemStackHandler getItemHandler() {
		return items;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.thaumcraft.research_table");
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
		return new ResearchTableMenu(containerId, inventory, this);
	}

	public ItemStack getInk() {
		return items.getStackInSlot(0);
	}

	public ItemStack getPaper() {
		return items.getStackInSlot(1);
	}

	public void startNewTheory(Player player, Set<String> aids) {
		data = new ResearchTableData(player, this);
		data.initialize(player, aids);
		setChanged();
	}

	/** Converts each category's raw percentage progress into real THEORY knowledge - the top
	 * {@code data.penaltyStart} categories (by progress) get the full amount, every category after that
	 * loses a third, matching the original's own "only your strongest theories count fully" rule. */
	public void finishTheory(Player player) {
		Comparator<Map.Entry<String, Integer>> valueComparator = (e1, e2) -> e2.getValue().compareTo(e1.getValue());
		Map<String, Integer> sortedMap = data.categoryTotals.entrySet().stream().sorted(valueComparator)
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
		int i = 0;
		for (String cat : sortedMap.keySet()) {
			int tot = Math.round(sortedMap.get(cat) / 100.0f * IPlayerKnowledge.EnumKnowledgeType.THEORY.getProgression());
			if (i > data.penaltyStart) {
				tot = (int) Math.max(1.0, tot * 0.666666667);
			}
			ResearchCategory rc = ResearchCategories.getResearchCategory(cat);
			ThaumcraftApi.internalMethods.addKnowledge(player, IPlayerKnowledge.EnumKnowledgeType.THEORY, rc, tot);
			i++;
		}
		data = null;
	}

	/** Scans a 9x9x3 area around the table for real registered aid blocks/items, plus a 5-block entity
	 * radius for entity-class aids - direct translation. {@link TheorycraftManager#aids} has no real
	 * entries yet in this port (aids are a real, separately-scoped follow-up - see this class's own arc
	 * notes), so this always returns an empty set for now, but the scan logic itself is real and ready. */
	public Set<String> checkSurroundingAids() {
		HashMap<String, ITheorycraftAid> mutators = new HashMap<>();
		Level currentLevel = level;
		if (currentLevel == null) return mutators.keySet();
		for (int y = -1; y <= 1; y++) {
			for (int x = -4; x <= 4; x++) {
				for (int z = -4; z <= 4; z++) {
					BlockPos checkPos = getBlockPos().offset(x, y, z);
					BlockState state = currentLevel.getBlockState(checkPos);
					for (String muk : TheorycraftManager.aids.keySet()) {
						ITheorycraftAid mu = TheorycraftManager.aids.get(muk);
						if (mu.getAidObject() instanceof Block block) {
							if (state.getBlock() != block) continue;
							mutators.put(muk, mu);
						} else if (mu.getAidObject() instanceof ItemStack aidStack) {
							ItemStack dropped = state.getBlock().asItem().getDefaultInstance();
							if (dropped.isEmpty() || !ItemStack.isSameItem(dropped, aidStack)) continue;
							mutators.put(muk, mu);
						}
					}
				}
			}
		}
		List<Entity> nearby = currentLevel.getEntitiesOfClass(Entity.class, new AABB(getBlockPos()).inflate(5.0));
		for (Entity e : nearby) {
			for (String muk : TheorycraftManager.aids.keySet()) {
				ITheorycraftAid mu = TheorycraftManager.aids.get(muk);
				if (mu.getAidObject() instanceof Class<?> clazz && clazz.isAssignableFrom(e.getClass())) {
					mutators.put(muk, mu);
				}
			}
		}
		return mutators.keySet();
	}

	public boolean consumeInkFromTable() {
		ItemStack ink = getInk();
		if (ink.getItem() instanceof IScribeTools && ink.getDamageValue() < ink.getMaxDamage()) {
			ink.setDamageValue(ink.getDamageValue() + 1);
			setChanged();
			return true;
		}
		return false;
	}

	public boolean consumePaperFromTable() {
		ItemStack paper = getPaper();
		if (paper.getItem() == Items.PAPER && paper.getCount() > 0) {
			items.extractItem(1, 1, false);
			setChanged();
			return true;
		}
		return false;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.put("Items", items.serializeNBT(registries));
		if (data != null) {
			tag.put("note", data.serialize());
		}
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		items.deserializeNBT(registries, tag.getCompound("Items"));
		if (tag.contains("note")) {
			data = new ResearchTableData(this);
			data.deserialize(tag.getCompound("note"));
		} else {
			data = null;
		}
	}
}
