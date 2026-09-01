package thaumcraft.common.tiles.devices;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.common.registry.ThaumcraftBlockEntities;

/**
 * A thin visibility shim over vanilla's {@link ChestBlockEntity} - see
 * {@link thaumcraft.common.blocks.devices.BlockHungryChest}'s own doc comment for why no real custom tile
 * class was originally planned here. {@code ChestBlockEntity}'s own 3-arg constructor (the one that lets a
 * subclass bind a non-vanilla {@code BlockEntityType}) is {@code protected}, so a same-package caller can't
 * reach it directly; its public 2-arg constructor unconditionally hardcodes vanilla's own
 * {@code BlockEntityType.CHEST}, which fails NeoForge's block-entity-type validation once placed under a
 * different block (found by actually running the GameTest, not by inspection - see the port-tracking
 * memory notes). This class exists purely to make that 3-arg constructor callable with the real registered
 * type; it adds no behavior of its own.
 */
public class TileHungryChest extends ChestBlockEntity {
	public TileHungryChest(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.HUNGRY_CHEST.get(), pos, state);
	}
}
