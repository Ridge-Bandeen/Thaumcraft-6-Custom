package thaumcraft.common.world.aura;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;

/**
 * Plain JUnit coverage for {@link AuraWorld#save}/{@link AuraWorld#load} - closes a gap flagged when aura
 * persistence was first built (verified boot-clean at the time, but never an actual save/load round trip
 * with real data, since nothing in an automated boot touches a chunk's aura). Both methods' {@code
 * HolderLookup.Provider registries} parameter is unused in the body, so this needs no live Level either -
 * passing {@code null} is fine, same reasoning as {@code PlayerKnowledgeTest}.
 */
class AuraWorldTest {

	@Test
	void saveAndLoadRoundTripsEveryChunkExactly() {
		AuraWorld original = new AuraWorld();
		original.getAuraChunks().put(ChunkPos.asLong(3, -5), new AuraChunk(new ChunkPos(3, -5), (short) 480, 250.5f, 12.25f));
		original.getAuraChunks().put(ChunkPos.asLong(0, 0), new AuraChunk(new ChunkPos(0, 0), (short) 500, 500.0f, 0.0f));

		CompoundTag tag = original.save(new CompoundTag(), null);
		AuraWorld restored = AuraWorld.load(tag, null);

		assertEquals(2, restored.getAuraChunks().size());

		AuraChunk chunkA = restored.getAuraChunkAt(3, -5);
		assertEquals(480, chunkA.getBase());
		assertEquals(250.5f, chunkA.getVis());
		assertEquals(12.25f, chunkA.getFlux());

		AuraChunk chunkB = restored.getAuraChunkAt(0, 0);
		assertEquals(500, chunkB.getBase());
		assertEquals(500.0f, chunkB.getVis());
		assertEquals(0.0f, chunkB.getFlux());
	}

	@Test
	void loadingAnEmptyTagProducesAnEmptyWorldNotAnException() {
		AuraWorld restored = AuraWorld.load(new CompoundTag(), null);
		assertEquals(0, restored.getAuraChunks().size());
		assertNull(restored.getAuraChunkAt(0, 0));
	}

	@Test
	void savingAnEmptyWorldAndReloadingItStaysEmpty() {
		AuraWorld original = new AuraWorld();
		CompoundTag tag = original.save(new CompoundTag(), null);
		AuraWorld restored = AuraWorld.load(tag, null);
		assertEquals(0, restored.getAuraChunks().size());
	}
}
