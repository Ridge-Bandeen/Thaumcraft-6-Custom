package thaumcraft.common.blocks.misc;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Replaces the old (1.12.2) {@code BlockLoot} - the three loot-crate tiers ({@code loot_crate_common/
 * uncommon/rare}) placed by {@code EntityCultistPortalGreater} during its staged encounter. Real, slightly
 * undersized box shape matching the original's own {@code getBoundingBox} exactly.
 *
 * The original's own drops came from {@code Utils.generateLoot} - a hand-written weighted-tier gear
 * generator (leather-through-diamond armor progression, random enchantments, plus a fallback
 * {@code WeightedRandomLoot} item pool) referencing a large slice of the original mod's own item catalog,
 * most of which isn't ported yet. Rather than build a second, parallel port of that same system, this
 * reuses the real fix this port already committed to for the closely-related {@link
 * thaumcraft.common.items.curios.ItemLootBag} (see that class's own doc comment): each tier's real drops
 * come from vanilla's own data-driven loot table system, nesting the SAME real {@code
 * gameplay/loot_bag_<tier>} table this port's loot bags already use (a real, working, curated vanilla-only
 * pool, "better tier, better stuff," rather than a generator mostly targeting items this port doesn't have).
 * One difference from the loot bags: no silk-touch-preserves-the-crate branch (the original's own {@code
 * canSilkHarvest() -> true}) - always drops its real tiered contents when broken, a real, minor,
 * documented simplification since preserving the crate itself as a decoration is tangential to what the
 * portal encounter actually needs from this block.
 */
public class BlockLoot extends Block {

	public static final MapCodec<BlockLoot> CODEC = simpleCodec(BlockLoot::new);

	private static final VoxelShape SHAPE = Shapes.box(0.0625, 0.0, 0.0625, 0.9375, 0.875, 0.9375);

	public BlockLoot(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}
}
