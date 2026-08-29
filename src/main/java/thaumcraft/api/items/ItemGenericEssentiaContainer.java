package thaumcraft.api.items;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaContainerItem;


public class ItemGenericEssentiaContainer extends Item implements IEssentiaContainerItem
{

	public ItemGenericEssentiaContainer(Properties properties, int base)
    {
        super(properties.stacksTo(64));
        this.base = base;
    }

	protected int base = 1;

	/**
	 * One stack per registered aspect, each carrying {@code base} amount of that aspect. Was an override of
	 * {@code Item#getSubItems(CreativeTabs, NonNullList<ItemStack>)}, which doesn't exist anymore - creative
	 * tab contents are populated externally now (via {@code BuildCreativeModeTabContentsEvent} or
	 * {@code CreativeModeTab.Builder#displayItems}), so whatever sets up this item's creative tab entry
	 * should call this instead.
	 */
	public List<ItemStack> createAspectVariants() {
		List<ItemStack> items = new ArrayList<>(Aspect.aspects.size());
		for (Aspect tag : Aspect.aspects.values()) {
			ItemStack i = new ItemStack(this);
			setAspects(i, new AspectList().add(tag, base));
			items.add(i);
		}
		return items;
	}

	@Override
	public AspectList getAspects(ItemStack itemstack) {
		AspectList aspects = itemstack.get(ThaumcraftDataComponents.ASPECTS.get());
		return aspects != null && aspects.size() > 0 ? aspects : null;
	}

	@Override
	public void setAspects(ItemStack itemstack, AspectList aspects) {
		itemstack.set(ThaumcraftDataComponents.ASPECTS.get(), aspects);
	}

	@Override
	public boolean ignoreContainedAspects() {return false;}

	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
		if (!level.isClientSide && !stack.has(ThaumcraftDataComponents.ASPECTS.get())) {
			Aspect[] displayAspects = Aspect.aspects.values().toArray(new Aspect[]{});
			setAspects(stack, new AspectList().add(displayAspects[level.random.nextInt(displayAspects.length)], base));
		}
		super.inventoryTick(stack, level, entity, slotId, isSelected);
	}

	@Override
	public void onCraftedBy(ItemStack stack, Level level, Player player) {
		if (!level.isClientSide && !stack.has(ThaumcraftDataComponents.ASPECTS.get())) {
			Aspect[] displayAspects = Aspect.aspects.values().toArray(new Aspect[]{});
			setAspects(stack, new AspectList().add(displayAspects[level.random.nextInt(displayAspects.length)], base));
		}
	}
}
