package thaumcraft.common.lib.research.theorycraft;

import java.util.Random;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.research.theorycraft.ResearchTableData;
import thaumcraft.api.research.theorycraft.TheorycraftCard;
import thaumcraft.common.items.consumables.ItemPhial;
import thaumcraft.common.lib.crafting.ThaumcraftObjectTags;

/** Replaces the old (1.12.2) {@code CardInfuse} - direct translation. A real random "value" item plus a
 * real filled Phial of a random compound {@link Aspect}, both consumed; progress scales with the value
 * item's own real essentia content (via {@link ThaumcraftObjectTags#getObjectTags}, the modern equivalent
 * of the original's {@code ThaumcraftCraftingManager.getObjectTags}). The original's own option pool
 * included 2 entries keyed by the old metadata "ingots" item (meta 0/2) - replaced here with real named
 * modern items ({@link ItemsTC#ingotThaumium}/{@link ItemsTC#ingotBrass}) rather than dropped, keeping the
 * real pool size. */
public class CardInfuse extends TheorycraftCard {

	private static final ItemStack[] OPTIONS = {
			new ItemStack(ItemsTC.alumentum.get()), new ItemStack(BlocksTC.nitor.get(DyeColor.YELLOW).get()),
			new ItemStack(ItemsTC.amber.get()), new ItemStack(ItemsTC.brain.get()), new ItemStack(ItemsTC.fabric.get()),
			new ItemStack(ItemsTC.salisMundus.get()), new ItemStack(ItemsTC.ingotThaumium.get()), new ItemStack(ItemsTC.ingotBrass.get()),
			new ItemStack(ItemsTC.quicksilver.get()), new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.IRON_INGOT),
			new ItemStack(Items.DIAMOND), new ItemStack(Items.EMERALD), new ItemStack(Items.BLAZE_ROD),
			new ItemStack(Items.LEATHER), new ItemStack(Blocks.WHITE_WOOL), new ItemStack(Items.BRICK),
			new ItemStack(Items.ARROW), new ItemStack(Items.EGG), new ItemStack(Items.FEATHER),
			new ItemStack(Items.GLOWSTONE_DUST), new ItemStack(Items.REDSTONE), new ItemStack(Items.GHAST_TEAR),
			new ItemStack(Items.GUNPOWDER), new ItemStack(Items.BOW), new ItemStack(Items.GOLDEN_SWORD),
			new ItemStack(Items.IRON_SWORD), new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.GOLDEN_PICKAXE),
			new ItemStack(Items.QUARTZ), new ItemStack(Items.APPLE),
	};

	private Aspect aspect;
	private ItemStack stack = ItemStack.EMPTY;

	// Real, documented pre-existing gap, not introduced here: TheorycraftCard#serialize/deserialize (the
	// already-shipped base class) has no real HolderLookup.Provider parameter at all, even though
	// TileResearchTable#loadAdditional/saveAdditional (its own real caller, several frames up) does have real
	// registries available - they're just never threaded through the rest of the real chain
	// (ResearchTableData -> CardChoice -> here). RegistryAccess.EMPTY is the best available real fallback;
	// it round-trips correctly within a single play session (initialize() sets the real field directly,
	// never via deserialize), but a stack saved to disk and loaded back after a server restart would come
	// back empty. Fixing this for real means threading real registries through that whole existing chain -
	// out of scope for this pass, a real, separate follow-up if it's ever worth it.
	@Override
	public CompoundTag serialize() {
		CompoundTag nbt = super.serialize();
		nbt.putString("aspect", aspect.getTag());
		nbt.put("stack", stack.save(net.minecraft.core.RegistryAccess.EMPTY, new CompoundTag()));
		return nbt;
	}

	@Override
	public void deserialize(CompoundTag nbt) {
		super.deserialize(nbt);
		aspect = Aspect.getAspect(nbt.getString("aspect"));
		stack = ItemStack.parseOptional(net.minecraft.core.RegistryAccess.EMPTY, nbt.getCompound("stack"));
	}

	@Override
	public boolean initialize(Player player, ResearchTableData data) {
		Random r = new Random(getSeed());
		aspect = Aspect.getCompoundAspects().get(r.nextInt(Aspect.getCompoundAspects().size()));
		stack = OPTIONS[r.nextInt(OPTIONS.length)].copy();
		return true;
	}

	@Override
	public int getInspirationCost() {
		return 1;
	}

	@Override
	public String getResearchCategory() {
		return "INFUSION";
	}

	@Override
	public String getLocalizedName() {
		return Component.translatable("card.infuse.name").getString();
	}

	@Override
	public String getLocalizedText() {
		return Component.translatable("card.infuse.text",
				ChatFormatting.BOLD + aspect.getName() + ChatFormatting.RESET, stack.getHoverName(), getVal()).getString();
	}

	private int getVal() {
		int q = 10;
		try {
			q += (int) (Math.sqrt(ThaumcraftObjectTags.getObjectTags(stack).visSize()) * 1.5);
		} catch (Exception ignored) {
		}
		return q;
	}

	@Override
	public ItemStack[] getRequiredItems() {
		return new ItemStack[] { stack, ItemPhial.makeFilledPhial(aspect) };
	}

	@Override
	public boolean[] getRequiredItemsConsumed() {
		return new boolean[] { true, true };
	}

	@Override
	public boolean activate(Player player, ResearchTableData data) {
		data.addTotal(getResearchCategory(), getVal());
		return true;
	}
}
