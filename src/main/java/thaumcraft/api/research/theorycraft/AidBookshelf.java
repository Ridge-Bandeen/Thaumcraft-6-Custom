package thaumcraft.api.research.theorycraft;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Replaces the old (1.12.2) {@code AidBookshelf} - direct translation, the last real file in this package
 * (the other 13 - the engine plus all 9 real API-tier cards - were already ported earlier this session).
 * A real Bookshelf placed within {@link thaumcraft.common.tiles.crafting.TileResearchTable}'s own 9x9x3
 * scan range adds 3x {@link CardStudy} and 2x {@link CardNotation} plus 1x {@link CardBalance} to the draw
 * pool for that theory - the original's exact card counts.
 */
public class AidBookshelf implements ITheorycraftAid {

	@Override
	public Object getAidObject() {
		return Blocks.BOOKSHELF;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Class<TheorycraftCard>[] getCards() {
		return new Class[] {CardBalance.class, CardNotation.class, CardNotation.class, CardStudy.class, CardStudy.class, CardStudy.class};
	}
}
