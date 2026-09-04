package thaumcraft.common.blocks.world.taint;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Replaces the old (1.12.2) {@code ITaintBlock} - the shared "this taint block has run out of nearby seed
 * support and reverts/dies" contract every taint block implements.
 */
public interface ITaintBlock {

	void die(Level level, BlockPos pos, BlockState state);
}
