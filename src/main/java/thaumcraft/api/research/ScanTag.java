package thaumcraft.api.research;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * A scan that matches whatever is in the {@code thaumcraft:scan/<name>} block, item and entity-type tags - so packs and
 * addons can change what unlocks a research flag with data alone. A block is also matched by its item; an item
 * entity by its item.
 */
public class ScanTag implements IScanThing {

	private final String research;
	private final TagKey<Block> blocks;
	private final TagKey<Item> items;
	private final TagKey<EntityType<?>> entities;

	public ScanTag(String research, @Nullable TagKey<Block> blocks, @Nullable TagKey<Item> items, @Nullable TagKey<EntityType<?>> entities) {
		this.research = research;
		this.blocks = blocks;
		this.items = items;
		this.entities = entities;
	}

	/** Scans {@code thaumcraft:scan/<tag>} in whichever of the three tag kinds are asked for. */
	public static ScanTag of(String research, String tag, boolean blocks, boolean items, boolean entities) {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("thaumcraft", "scan/" + tag);
		return new ScanTag(research, blocks ? TagKey.create(Registries.BLOCK, id) : null, items ? TagKey.create(Registries.ITEM, id) : null,
				entities ? TagKey.create(Registries.ENTITY_TYPE, id) : null);
	}

	@Override
	public boolean checkThing(Player player, Object obj) {
		if (obj instanceof BlockPos pos) {
			var state = player.level().getBlockState(pos);
			if (blocks != null && state.is(blocks)) return true;
			return items != null && new ItemStack(state.getBlock().asItem()).is(items);
		}
		ItemStack stack = obj instanceof ItemStack is ? is : obj instanceof ItemEntity ie ? ie.getItem() : null;
		if (stack != null) return items != null && !stack.isEmpty() && stack.is(items);
		if (obj instanceof Entity entity) return entities != null && entity.getType().is(entities);
		return false;
	}

	@Override
	public String getResearchKey(Player player, Object object) {
		return research;
	}
}
