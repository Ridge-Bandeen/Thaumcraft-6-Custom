package thaumcraft.common.lib.research.theorycraft;

import java.util.Random;

import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.research.theorycraft.ResearchTableData;
import thaumcraft.api.research.theorycraft.TheorycraftCard;
import thaumcraft.common.lib.crafting.ThaumcraftObjectTags;

/** Replaces the old (1.12.2) {@code CardTinker} - direct translation, {@link CardMindOverMatter}'s own
 * ARTIFICE counterpart (shares the same real option pool and progress formula, shown but not consumed here
 * rather than consumed). See {@link CardInfuse}'s own doc comment for the real, pre-existing NBT-round-trip
 * gap this shares (no real registries reach {@code TheorycraftCard#deserialize}). */
public class CardTinker extends TheorycraftCard {

	private static final ItemStack[] OPTIONS = {
			new ItemStack(ItemsTC.visResonator.get()), new ItemStack(ItemsTC.thaumometer.get()),
			new ItemStack(Blocks.ANVIL), new ItemStack(Blocks.ACTIVATOR_RAIL), new ItemStack(Blocks.DISPENSER),
			new ItemStack(Blocks.DROPPER), new ItemStack(Blocks.ENCHANTING_TABLE), new ItemStack(Blocks.ENDER_CHEST),
			new ItemStack(Blocks.JUKEBOX), new ItemStack(Blocks.DAYLIGHT_DETECTOR), new ItemStack(Blocks.PISTON),
			new ItemStack(Blocks.HOPPER), new ItemStack(Blocks.STICKY_PISTON), new ItemStack(Items.MAP),
			new ItemStack(Items.COMPASS), new ItemStack(Items.TNT_MINECART), new ItemStack(Items.COMPARATOR),
			new ItemStack(Items.CLOCK),
	};

	private ItemStack stack = ItemStack.EMPTY;

	@Override
	public CompoundTag serialize() {
		CompoundTag nbt = super.serialize();
		nbt.put("stack", stack.save(net.minecraft.core.RegistryAccess.EMPTY, new CompoundTag()));
		return nbt;
	}

	@Override
	public void deserialize(CompoundTag nbt) {
		super.deserialize(nbt);
		stack = ItemStack.parseOptional(net.minecraft.core.RegistryAccess.EMPTY, nbt.getCompound("stack"));
	}

	@Override
	public boolean initialize(Player player, ResearchTableData data) {
		Random r = new Random(getSeed());
		stack = OPTIONS[r.nextInt(OPTIONS.length)].copy();
		return true;
	}

	@Override
	public int getInspirationCost() {
		return 1;
	}

	@Override
	public String getResearchCategory() {
		return "ARTIFICE";
	}

	private int getVal() {
		int q = 0;
		try {
			q += (int) Math.sqrt(ThaumcraftObjectTags.getObjectTags(stack).visSize());
		} catch (Exception ignored) {
		}
		return q;
	}

	@Override
	public String getLocalizedName() {
		return Component.translatable("card.tinker.name").getString();
	}

	@Override
	public String getLocalizedText() {
		int a = getVal() * 2;
		int b = a + 10;
		return Component.translatable("card.tinker.text", a, b).getString();
	}

	@Override
	public ItemStack[] getRequiredItems() {
		return new ItemStack[] { stack };
	}

	@Override
	public boolean activate(Player player, ResearchTableData data) {
		int q = getVal() * 2;
		data.addTotal(getResearchCategory(), Mth.nextInt(player.getRandom(), q, q + 10));
		return true;
	}
}
