package thaumcraft.common.blocks.misc;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.common.lib.utils.LootGenerator;

/**
 * Replaces the old (1.12.2) {@code BlockLoot} - the three loot-crate tiers ({@code loot_crate_common/uncommon/rare})
 * placed by {@code EntityCultistPortalGreater} during its staged encounter, plus the stone urns
 * ({@code loot_urn_common/uncommon/rare}) found in the world. As the original: breaking one gives 1 + tier + 0-2 items from
 * the loot generator ({@link LootGenerator}, the same as loot bags); with Silk Touch you get the urn or crate itself; and
 * urns are a little smaller than crates. The urns' "urnbreak" sound is set on their registration in {@code BlocksTC}.
 */
public class BlockLoot extends Block {

	public static final MapCodec<BlockLoot> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.INT.fieldOf("rarity").forGetter(b -> b.rarity),
			Codec.BOOL.fieldOf("urn").forGetter(b -> b.urn),
			propertiesCodec()).apply(i, (rarity, urn, props) -> new BlockLoot(props, rarity, urn)));

	private static final VoxelShape CRATE = Shapes.box(0.0625, 0.0, 0.0625, 0.9375, 0.875, 0.9375);
	private static final VoxelShape URN = Shapes.box(0.125, 0.0625, 0.125, 0.875, 0.8125, 0.875);

	/** 0 common, 1 uncommon, 2 rare. */
	private final int rarity;
	private final boolean urn;

	public BlockLoot(BlockBehaviour.Properties properties, int rarity, boolean urn) {
		super(properties);
		this.rarity = rarity;
		this.urn = urn;
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return urn ? URN : CRATE;
	}

	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		var level = params.getLevel();
		ItemStack tool = params.getOptionalParameter(LootContextParams.TOOL);
		if (tool != null && !tool.isEmpty() && EnchantmentHelper.getItemEnchantmentLevel(
				level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SILK_TOUCH), tool) > 0) {
			return List.of(new ItemStack(this));
		}
		Float explosion = params.getOptionalParameter(LootContextParams.EXPLOSION_RADIUS);
		List<ItemStack> drops = new ArrayList<>();
		for (int q = 1 + rarity + level.random.nextInt(3), a = 0; a < q; ++a) {
			ItemStack loot = LootGenerator.generateLoot(rarity, level.random, level.registryAccess());
			// as the original's dropBlockAsItemWithChance: an explosion keeps each item with a chance of 1 / its size
			if (!loot.isEmpty() && (explosion == null || level.random.nextFloat() <= 1.0f / explosion)) drops.add(loot);
		}
		return drops;
	}
}
