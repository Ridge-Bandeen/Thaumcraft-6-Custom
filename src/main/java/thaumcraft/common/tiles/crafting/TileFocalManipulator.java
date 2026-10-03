package thaumcraft.common.tiles.crafting;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.casters.FocusModSplit;
import thaumcraft.api.casters.FocusPackage;
import thaumcraft.api.items.ItemGenericEssentiaContainer;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.items.ThaumcraftDataComponents;
import thaumcraft.common.container.FocalManipulatorMenu;
import thaumcraft.common.items.casters.ItemFocus;
import thaumcraft.common.registry.ThaumcraftBlockEntities;
import thaumcraft.common.registry.ThaumcraftSounds;
import thaumcraft.common.world.aura.AuraHandler;

/**
 * Replaces the old (1.12.2) {@code TileFocalManipulator} - where a player assembles a real node graph onto
 * a blank {@link thaumcraft.common.items.casters.ItemFocusBlank} (build cost paid in vis + matching essentia
 * crystals + XP), rather than being stuck with the fixed-chain foci this session shipped earlier as a
 * stand-in for this exact device. See {@link ItemFocus}'s own doc comment for how the two coexist.
 *
 * Purpose-built {@link BlockEntity} + real {@link ItemStackHandler}, same "no shared inventory-tile base"
 * pattern {@code TileRechargePedestal}/{@code TileCrucible} already established - see that class's own doc
 * comment for why. {@link #nodes} is the live, player-editable graph. As the original it draws its vis 20 at a time
 * every second (a 3x3 chunk area under an Arcane Workbench Charger), sparkling (block event 5), with its own
 * sounds; {@link #hasCrystals}/{@link #consumeCrystals} check and take the crystals the build needs from the
 * player's inventory.
 *
 * {@link #getUpdateTag}/{@link #getUpdatePacket}/{@link #setChanged()} are overridden below - the same real
 * client-sync fix found while scoping the Infusion Matrix renderer (see
 * {@link thaumcraft.common.tiles.crafting.TilePedestal#setChanged()}'s own doc comment): without them, this
 * tile's held focus and staged crystals would never reach any client at all, which
 * {@link thaumcraft.client.render.TileFocalManipulatorRenderer} needs to show either.
 */
public class TileFocalManipulator extends BlockEntity implements MenuProvider {

	private final ItemStackHandler items = new ItemStackHandler(1) {
		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return stack.getItem() instanceof ItemFocus;
		}

		@Override
		protected void onContentsChanged(int slot) {
			setChanged();
			if (vis > 0.0f) {
				vis = 0.0f;
				crystalsSync = new AspectList();
			}
		}
	};

	public float vis;
	public final Map<Integer, FocusElementNode> nodes = new HashMap<>();
	public String focusName = "";
	private int ticks;
	private AspectList crystals = new AspectList();
	public AspectList crystalsSync = new AspectList();
	private int xpCost;

	public TileFocalManipulator(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.FOCAL_MANIPULATOR.get(), pos, state);
	}

	public ItemStack getFocus() {
		return items.getStackInSlot(0);
	}

	public void setFocus(ItemStack stack) {
		items.setStackInSlot(0, stack);
	}

	public net.neoforged.neoforge.items.IItemHandler getItemHandler() {
		return items;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.thaumcraft.focal_manipulator");
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
		return new FocalManipulatorMenu(containerId, inventory, this);
	}

	/** Draws vis from the local aura, or (if an Arcane Workbench Charger sits directly above) spreads the
	 * draw across a 3x3 grid of remote 16-block-spaced aura chunks instead - direct translation of the
	 * original's own multiblock-charging behaviour. */
	private float spendAura(Level level, float amount) {
		if (level.getBlockState(getBlockPos().above()).getBlock() == BlocksTC.arcaneWorkbenchCharger.get()) {
			float remaining = amount;
			float perChunk = amount / 9.0f;
			outer:
			for (int xx = -1; xx <= 1; xx++) {
				for (int zz = -1; zz <= 1; zz++) {
					float draw = Math.min(perChunk, remaining);
					remaining -= AuraHandler.drainVis(level, getBlockPos().offset(xx * 16, 0, zz * 16), draw, false);
					if (remaining <= 0.0f) {
						break outer;
					}
				}
			}
			return amount - remaining;
		}
		return AuraHandler.drainVis(level, getBlockPos(), amount, false);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TileFocalManipulator tile) {
		if (level.isClientSide) {
			return;
		}
		tile.ticks++;
		if (tile.ticks % 20 != 0) {
			return;
		}
		if (tile.vis <= 0.0f) {
			return;
		}
		ItemStack focus = tile.getFocus();
		if (focus.isEmpty() || !(focus.getItem() instanceof ItemFocus)) {
			tile.vis = 0.0f;
			level.playSound(null, pos, ThaumcraftSounds.WANDFAIL.get(), SoundSource.BLOCKS, 0.33f, 1.0f);
			return;
		}
		float amt = tile.spendAura(level, Math.min(20.0f, tile.vis));
		if (amt > 0.0f) {
			level.blockEvent(pos, state.getBlock(), 5, 1);
			tile.vis -= amt;
			tile.setChanged();
		}
		if (tile.vis <= 0.0f) {
			tile.endCraft(level, pos);
		}
	}

	/** Real vanilla entity-event trigger - see {@link #tick}'s own real {@code level.blockEvent} call. Real,
	 * faithful port of the true original's own real {@code receiveClientEvent(5, ...)} vis-spend sparkle cue,
	 * routed through {@link thaumcraft.client.fx.ClientFxHandlers#spawnVisSparkle} for the same real
	 * dist-crash-avoidance reason every other client-only trigger in this port uses. */
	@Override
	public boolean triggerEvent(int id, int type) {
		if (id == 5) {
			if (level != null && level.isClientSide) {
				thaumcraft.client.fx.ClientFxHandlers.spawnVisSparkle(level,
						worldPosition.getX() + level.random.nextInt(3) - level.random.nextInt(3),
						worldPosition.getY() + level.random.nextInt(3),
						worldPosition.getZ() + level.random.nextInt(3) - level.random.nextInt(3),
						worldPosition.getX(), worldPosition.getY() + 1, worldPosition.getZ(), type);
			}
			return true;
		}
		return super.triggerEvent(id, type);
	}

	private FocusPackage generateFocus() {
		if (nodes.isEmpty()) {
			return null;
		}
		FocusPackage core = new FocusPackage();
		int totalComplexity = 0;
		Map<String, Integer> compCount = new HashMap<>();
		for (FocusElementNode node : nodes.values()) {
			if (node.node != null) {
				int a = compCount.getOrDefault(node.node.getKey(), 0) + 1;
				node.complexityMultiplier = 0.5f * (a + 1);
				compCount.put(node.node.getKey(), a);
				totalComplexity += (int) (node.node.getComplexity() * node.complexityMultiplier);
			}
		}
		core.setComplexity(totalComplexity);
		traverseChildren(core, nodes.get(0));
		return core;
	}

	private void traverseChildren(FocusPackage currentPackage, FocusElementNode currentNode) {
		if (currentPackage == null || currentNode == null) {
			return;
		}
		currentPackage.addNode(currentNode.node);
		if (currentNode.children.length == 0) {
			return;
		}
		if (currentNode.children.length == 1) {
			traverseChildren(currentPackage, nodes.get(currentNode.children[0]));
		} else if (currentNode.node instanceof FocusModSplit splitNode) {
			splitNode.getSplitPackages().clear();
			for (int c : currentNode.children) {
				FocusPackage splitPackage = new FocusPackage();
				traverseChildren(splitPackage, nodes.get(c));
				splitNode.getSplitPackages().add(splitPackage);
			}
		}
	}

	private void endCraft(Level level, BlockPos pos) {
		vis = 0.0f;
		ItemStack focus = getFocus();
		if (focus.isEmpty() || !(focus.getItem() instanceof ItemFocus)) {
			return;
		}
		FocusPackage core = generateFocus();
		if (core == null) {
			return;
		}
		level.playSound(null, pos, ThaumcraftSounds.WAND.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
		focus.set(ThaumcraftDataComponents.ASSEMBLED_FOCUS.get(), new ThaumcraftDataComponents.AssembledFocus(core));
		if (!focusName.isEmpty()) {
			focus.set(DataComponents.CUSTOM_NAME, Component.literal(focusName));
		}
		setFocus(focus);
		crystalsSync = new AspectList();
		nodes.clear();
		setChanged();
	}

	/** Validates and (on success) charges up the craft - vis/XP/crystal cost, gated by real per-node
	 * research (see {@link ThaumcraftCapabilities#knowsResearchStrict}). Direct translation of the
	 * original's own {@code startCraft}. */
	public boolean startCraft(Player player) {
		ItemStack focus = getFocus();
		if (nodes.isEmpty() || vis > 0.0f || focus.isEmpty() || !(focus.getItem() instanceof ItemFocus item)) {
			return false;
		}
		int maxComplexity = item.getMaxComplexity();
		int totalComplexity = 0;
		AspectList newCrystals = new AspectList();
		Map<String, Integer> compCount = new HashMap<>();
		for (FocusElementNode node : nodes.values()) {
			if (node.node == null) {
				return false;
			}
			if (!ThaumcraftCapabilities.knowsResearchStrict(player, node.node.getResearch())) {
				return false;
			}
			int a = compCount.getOrDefault(node.node.getKey(), 0) + 1;
			node.complexityMultiplier = 0.5f * (a + 1);
			compCount.put(node.node.getKey(), a);
			totalComplexity += (int) (node.node.getComplexity() * node.complexityMultiplier);
			if (node.node.getAspect() != null) {
				newCrystals.add(node.node.getAspect(), 1);
			}
		}
		if (totalComplexity > maxComplexity) {
			return false;
		}
		vis = totalComplexity * 10 + maxComplexity / 5;
		xpCost = (int) Math.max(1L, Math.round(Math.sqrt(totalComplexity)));
		if (!player.getAbilities().instabuild && player.experienceLevel < xpCost) {
			vis = 0.0f;
			return false;
		}
		if (newCrystals.getAspects().length == 0) {
			vis = 0.0f;
			return false;
		}
		if (!hasCrystals(player, newCrystals)) {
			vis = 0.0f;
			return false;
		}
		if (!player.getAbilities().instabuild) {
			player.giveExperienceLevels(-xpCost);
		}
		consumeCrystals(player, newCrystals);
		crystals = newCrystals;
		crystalsSync = newCrystals.copy();
		setChanged();
		Level level = getLevel();
		if (level != null) {
			level.playSound(null, getBlockPos(), ThaumcraftSounds.CRAFTSTART.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
		}
		return true;
	}

	private boolean hasCrystals(Player player, AspectList cost) {
		for (Aspect aspect : cost.getAspects()) {
			int needed = cost.getAmount(aspect);
			int have = player.getInventory().clearOrCountMatchingItems(crystalPredicate(aspect), 0, player.getInventory());
			if (have < needed) {
				return false;
			}
		}
		return true;
	}

	private void consumeCrystals(Player player, AspectList cost) {
		for (Aspect aspect : cost.getAspects()) {
			player.getInventory().clearOrCountMatchingItems(crystalPredicate(aspect), cost.getAmount(aspect), player.getInventory());
		}
	}

	private static Predicate<ItemStack> crystalPredicate(Aspect aspect) {
		return stack -> {
			if (stack.getItem() != ItemsTC.crystalEssence.get()) {
				return false;
			}
			AspectList held = ((ItemGenericEssentiaContainer) stack.getItem()).getAspects(stack);
			return held != null && held.size() == 1 && held.getAmount(aspect) == 1;
		};
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.put("Items", items.serializeNBT(registries));
		tag.putFloat("vis", vis);
		tag.putString("focusName", focusName);
		crystalsSync.writeToNBT(tag, "crystals");
		ListTag nodeList = new ListTag();
		for (FocusElementNode node : nodes.values()) {
			nodeList.add(node.serialize());
		}
		tag.put("nodes", nodeList);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		items.deserializeNBT(registries, tag.getCompound("Items"));
		vis = tag.getFloat("vis");
		focusName = tag.getString("focusName");
		crystalsSync = new AspectList();
		crystalsSync.readFromNBT(tag, "crystals");
		nodes.clear();
		ListTag nodeList = tag.getList("nodes", net.minecraft.nbt.Tag.TAG_COMPOUND);
		for (int i = 0; i < nodeList.size(); i++) {
			FocusElementNode node = new FocusElementNode();
			node.deserialize(nodeList.getCompound(i));
			nodes.put(node.id, node);
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

	/** See {@link thaumcraft.common.tiles.crafting.TilePedestal#setChanged()}'s own doc comment - same real
	 * fix, same reason. */
	@Override
	public void setChanged() {
		super.setChanged();
		if (level != null && !level.isClientSide) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
		}
	}
}
